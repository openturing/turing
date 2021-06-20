turingApp.factory('turNLPVendorResource', [
		'$resource',
		'turAPIServerService',
		function($resource, turAPIServerService) {
			return $resource(turAPIServerService.get()
					.concat('/nlp/vendor/:id'), {
				id : '@id'
			}, {
				update : {
					method : 'PUT'
				}
			});
		} ]);