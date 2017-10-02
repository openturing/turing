turingApp.factory('turStorageInstanceResource', [ '$resource',
		'turAPIServerService', function($resource, turAPIServerService) {
			return $resource(turAPIServerService.get().concat('/storage/:id'), {
				id : '@id'
			}, {
				update : {
					method : 'PUT'
				}
			});
		} ]);