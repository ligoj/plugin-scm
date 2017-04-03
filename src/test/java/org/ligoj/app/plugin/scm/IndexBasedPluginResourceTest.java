package org.ligoj.app.plugin.scm;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.ligoj.app.AbstractServerTest;
import org.ligoj.app.MatcherUtil;
import org.ligoj.app.api.SubscriptionStatusWithData;
import org.ligoj.app.resource.node.NodeResource;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.ligoj.bootstrap.core.NamedBean;
import org.ligoj.bootstrap.core.json.InMemoryPagination;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/**
 * Test class of {@link AbstractIndexBasedPluginResource}
 */
public class IndexBasedPluginResourceTest extends AbstractServerTest {
	private AbstractIndexBasedPluginResource resource;
	private SubscriptionResource subscriptionResource;
	private Map<String, String> parameters;

	@SuppressWarnings("unchecked")
	@Before
	public void newMockResource() {
		resource = new AbstractIndexBasedPluginResource("service", "impl") {

			@Override
			protected String getRepositoryUrl(final Map<String, String> parameters) {
				return StringUtils.appendIfMissing(super.getRepositoryUrl(parameters), "/");
			}

			/**
			 * Return the revision number.
			 */
			@Override
			protected Object toData(final String statusContent) {
				return 1;
			}

			{
				subscriptionResource = Mockito.mock(SubscriptionResource.class);
				parameters = new HashMap<>();
				parameters.put("service:url", "http://localhost:" + MOCK_PORT);
				parameters.put("service:user", "user");
				parameters.put("service:password", "secret");
				parameters.put("service:index", "true");
				parameters.put("service:repository", "my-repo");
				Mockito.when(subscriptionResource.getParameters(1)).thenReturn(parameters);
				Mockito.when(subscriptionResource.getParametersNoCheck(1)).thenReturn(parameters);
				IndexBasedPluginResourceTest.this.subscriptionResource = subscriptionResource;

				nodeResource = Mockito.mock(NodeResource.class);
				Mockito.when(nodeResource.getParametersAsMap("service:impl:node")).thenReturn(parameters);

				inMemoryPagination = Mockito.mock(InMemoryPagination.class);
				Mockito.when(inMemoryPagination.newPage(ArgumentMatchers.anyCollection(),
						ArgumentMatchers.any(Pageable.class)))
						.thenAnswer(i -> new PageImpl<>(new ArrayList<>((Collection<Object>) i.getArguments()[0]),
								(Pageable) i.getArguments()[1], ((Collection<Object>) i.getArguments()[0]).size()));
			}
		};
	}

	@Test
	public void getLastVersion() throws Exception {
		Assert.assertNull(resource.getLastVersion());
	}

	@Test
	public void link() throws Exception {
		prepareMockRepository();
		httpServer.start();

		// Invoke create for an already created entity, since for now, there is
		// nothing but validation pour SonarQube
		resource.link(1);

		// Nothing to validate for now...
	}

	@Test
	public void linkNotFound() throws Exception {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher("service:repository", "impl-repository"));

		prepareMockRepository();
		httpServer.start();

		parameters.put("service:repository", "any");

		// Invoke create for an already created entity, since for now, there is
		// nothing but validation pour SonarQube
		resource.link(1);

		// Nothing to validate for now...
	}

	@Test
	public void checkSubscriptionStatus() throws Exception {
		prepareMockRepository();
		final SubscriptionStatusWithData nodeStatusWithData = resource
				.checkSubscriptionStatus(subscriptionResource.getParametersNoCheck(1));
		Assert.assertTrue(nodeStatusWithData.getStatus().isUp());
		Assert.assertEquals(1, nodeStatusWithData.getData().get("info"));
	}

	private void prepareMockRepository() throws IOException {
		httpServer.stubFor(get(urlPathEqualTo("/my-repo/")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)
				.withBody(IOUtils.toString(new ClassPathResource("mock-server/scm/my-repo.html").getInputStream(),
						StandardCharsets.UTF_8))));
		httpServer.start();
	}

	private void prepareMockAdmin() throws IOException {
		httpServer.stubFor(get(urlPathEqualTo("/")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)
				.withBody(IOUtils.toString(new ClassPathResource("mock-server/scm/index.html").getInputStream(),
						StandardCharsets.UTF_8))));
		httpServer.start();
	}

	@Test
	public void checkStatus() throws Exception {
		prepareMockAdmin();
		Assert.assertTrue(resource.checkStatus(subscriptionResource.getParametersNoCheck(1)));
	}

	@Test
	public void checkStatusAuthenticationFailed() throws Exception {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher("service:url", "impl-admin"));
		httpServer.start();
		resource.checkStatus(subscriptionResource.getParametersNoCheck(1));
	}

	@Test
	public void checkStatusNotAdmin() throws Exception {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher("service:url", "impl-admin"));
		httpServer.stubFor(get(urlPathEqualTo("/")).willReturn(aResponse().withStatus(HttpStatus.SC_NOT_FOUND)));
		httpServer.start();
		resource.checkStatus(subscriptionResource.getParametersNoCheck(1));
	}

	@Test
	public void checkStatusInvalidIndex() throws Exception {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher("service:url", "impl-admin"));
		httpServer.stubFor(get(urlPathEqualTo("/"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("<html>some</html>")));
		httpServer.start();
		resource.checkStatus(subscriptionResource.getParametersNoCheck(1));
	}

	@Test
	public void findAllByName() throws Exception {
		prepareMockAdmin();
		httpServer.start();

		final List<NamedBean<String>> projects = resource.findAllByName("service:impl:node", "as-");
		Assert.assertEquals(4, projects.size());
		Assert.assertEquals("has-evamed", projects.get(0).getId());
		Assert.assertEquals("has-evamed", projects.get(0).getName());
	}

	@Test
	public void findAllByNameNoListing() throws Exception {
		httpServer.start();

		final List<NamedBean<String>> projects = resource.findAllByName("service:impl:node", "as-");
		Assert.assertEquals(0, projects.size());
	}

	@Test
	public void checkStatusNoIndex() throws Exception {
		prepareMockAdmin();
		parameters.put("service:index", "false");
		Assert.assertTrue(resource.checkStatus(subscriptionResource.getParametersNoCheck(1)));
	}

	@Test
	public void checkStatusNotHttp() throws Exception {
		prepareMockAdmin();
		parameters.put("service:url", "hq://");
		Assert.assertTrue(resource.checkStatus(subscriptionResource.getParametersNoCheck(1)));
	}

	@Test
	public void getKey() throws IOException {
		// Coverage only
		Assert.assertEquals("service:scm:impl", resource.getKey());
	}

	@Test
	protected void toData(final String statusContent) {
		Assert.assertEquals("some", new AbstractIndexBasedPluginResource("service", "impl") {
		}.toData("some"));
	}
}
