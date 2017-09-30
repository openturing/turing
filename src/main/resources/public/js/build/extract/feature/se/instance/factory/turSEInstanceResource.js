turingApp.factory('turSEInstanceResource', [ '$resource',
		'turAPIServerService', function($resource, turAPIServerService) {
			return $resource(turAPIServerService.get().concat('/se/:id'), {
				id : '@id'
			}, {
				update : {
					method : 'PUT'
				}
			});
		} ]);