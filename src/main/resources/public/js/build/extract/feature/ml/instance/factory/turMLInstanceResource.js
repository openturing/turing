turingApp.factory('turMLInstanceResource', [ '$resource',
		'turAPIServerService', function($resource, turAPIServerService) {
			return $resource(turAPIServerService.get().concat('/ml/:id'), {
				id : '@id'
			}, {
				update : {
					method : 'PUT'
				}
			});
		} ]);