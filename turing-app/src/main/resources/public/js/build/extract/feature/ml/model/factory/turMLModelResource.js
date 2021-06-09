turingApp.factory('turMLModelResource', [
		'$resource',
		'turAPIServerService',
		function($resource, turAPIServerService) {
			return $resource(turAPIServerService.get().concat('/ml/model/:id'),
					{
						id : '@id'
					}, {
						update : {
							method : 'PUT'
						}
					});
		} ]);