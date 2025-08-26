package org.ligoj.app.plugin.scm;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.Strings;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ligoj.app.AbstractServerTest;
import org.ligoj.app.resource.node.ParameterValueResource;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.ligoj.bootstrap.MatcherUtil;
import org.ligoj.bootstrap.core.json.InMemoryPagination;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Test class of {@link AbstractIndexBasedPluginResource}
 */
class IndexBasedPluginResourceTest extends AbstractServerTest {
	private AbstractIndexBasedPluginResource resource;
	private SubscriptionResource subscriptionResource;
	private Map<String, String> parameters;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void newMockResource() {
		resource = new AbstractIndexBasedPluginResource("service", "impl") {

			@Override
			protected String getRepositoryUrl(final Map<String, String> parameters) {
				return Strings.CS.appendIfMissing(super.getRepositoryUrl(parameters), "/");
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

				pvResource = Mockito.mock(ParameterValueResource.class);
				Mockito.when(pvResource.getNodeParameters("service:impl:node")).thenReturn(parameters);

				inMemoryPagination = Mockito.mock(InMemoryPagination.class);
				Mockito.when(inMemoryPagination.newPage(ArgumentMatchers.anyCollection(), ArgumentMatchers.any(Pageable.class)))
						.thenAnswer(i -> new PageImpl<>(new ArrayList<>((Collection<Object>) i.getArguments()[0]),
								(Pageable) i.getArguments()[1], ((Collection<Object>) i.getArguments()[0]).size()));
			}
		};
	}

	@Test
	void getLastVersion() throws Exception {
		Assertions.assertNull(resource.getLastVersion());
	}

	@Test
	void link() throws Exception {
		prepareMockRepository();
		httpServer.start();

		// Invoke create for an already created entity, since for now, there is
		// nothing but validation pour SonarQube
		resource.link(1);

		// Nothing to validate for now...
	}

	@Test
	void linkNotFound() throws Exception {
		prepareMockRepository();
		httpServer.start();

		parameters.put("service:repository", "any");

		// Invoke create for an already created entity, since for now, there is
		// nothing but validation pour SonarQube
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.link(1)), "service:repository", "impl-repository");
	}

	@Test
	void checkSubscriptionStatus() throws Exception {
		prepareMockRepository();
		final var nodeStatusWithData = resource.checkSubscriptionStatus(subscriptionResource.getParametersNoCheck(1));
		Assertions.assertTrue(nodeStatusWithData.getStatus().isUp());
		Assertions.assertEquals(1, nodeStatusWithData.getData().get("info"));
	}

	private void prepareMockRepository() throws IOException {
		httpServer.stubFor(get(urlPathEqualTo("/my-repo/")).willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(
				IOUtils.toString(new ClassPathResource("mock-server/scm/my-repo.html").getInputStream(), StandardCharsets.UTF_8))));
		httpServer.start();
	}

	private void prepareMockAdmin() throws IOException {
		httpServer.stubFor(get(urlPathEqualTo("/")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)
				.withBody(IOUtils.toString(new ClassPathResource("mock-server/scm/index.html").getInputStream(), StandardCharsets.UTF_8))));
		httpServer.start();
	}

	@Test
	void checkStatus() throws Exception {
		prepareMockAdmin();
		Assertions.assertTrue(resource.checkStatus(subscriptionResource.getParametersNoCheck(1)));
	}

	@Test
	void checkStatusAuthenticationFailed() {
		httpServer.start();
		final var subscriptionId = subscriptionResource.getParametersNoCheck(1);
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.checkStatus(subscriptionId)), "service:url", "impl-admin");
	}

	@Test
	void checkStatusNotAdmin() {
		httpServer.stubFor(get(urlPathEqualTo("/")).willReturn(aResponse().withStatus(HttpStatus.SC_NOT_FOUND)));
		httpServer.start();
		final var subscriptionId = subscriptionResource.getParametersNoCheck(1);
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.checkStatus(subscriptionId)), "service:url", "impl-admin");
	}

	@Test
	void checkStatusInvalidIndex() {
		httpServer.stubFor(get(urlPathEqualTo("/")).willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("<html>some</html>")));
		httpServer.start();
		final var subscriptionId = subscriptionResource.getParametersNoCheck(1);
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.checkStatus(subscriptionId)), "service:url", "impl-admin");
	}

	@Test
	void findAllByName() throws Exception {
		prepareMockAdmin();
		httpServer.start();

		final var projects = resource.findAllByName("service:impl:node", "as-");
		Assertions.assertEquals(4, projects.size());
		Assertions.assertEquals("has-event", projects.getFirst().getId());
		Assertions.assertEquals("has-event", projects.getFirst().getName());
	}

	@Test
	void findAllByNameNoListing() {
		httpServer.start();

		final var projects = resource.findAllByName("service:impl:node", "as-");
		Assertions.assertEquals(0, projects.size());
	}

	@Test
	void checkStatusNoIndex() throws Exception {
		prepareMockAdmin();
		parameters.put("service:index", "false");
		Assertions.assertTrue(resource.checkStatus(subscriptionResource.getParametersNoCheck(1)));
	}

	@Test
	void checkStatusNotHttp() throws Exception {
		prepareMockAdmin();
		parameters.put("service:url", "hq://");
		Assertions.assertTrue(resource.checkStatus(subscriptionResource.getParametersNoCheck(1)));
	}

	@Test
	void getKey() {
		// Coverage only
		Assertions.assertEquals("service", resource.getKey());
	}

	@Test
	void toData() {
		Assertions.assertEquals("some", new AbstractIndexBasedPluginResource("service:scm:impl", "impl") {
			// Nothing to change
		}.toData("some"));
	}
}
