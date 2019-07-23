define(function () {
	var current = {

		clipboard: null,

		initialize: function () {
			require(['clipboard/clipboard'], function (ClipboardJS) {
				current.clipboard = ClipboardJS;
				new ClipboardJS('.service-scm-clipboard', {
					text: function (trigger) {
						return $(trigger).prev('a.feature').attr('href');
					}
				});
			});
		},

		/**
		 * Render SCM home page.
		 */
		renderFeaturesScm: function (subscription, type) {
			// Add URL link
			var url = current.$super('getData')(subscription, 'service:scm:' + type + ':url') + '/' + current.$super('getData')(subscription, 'service:scm:' + type + ':repository');
			var result = current.$super('renderServiceLink')('home', url, 'service:scm:' + type + ':repository', null, 'target="_blank"');

			// Add Copy URL
			result += '<button class="btn-link service-scm-clipboard" data-toggle="tooltip" title="' + current.$messages['copy-clipboard'] + '" data-container="body"><i class="far fa-clipboard"></i></button>';

			// Help
			result += current.$super('renderServiceHelpLink')(subscription.parameters, 'service:scm:' + type + ':help');
			return result;
		}
	};
	return current;
});
