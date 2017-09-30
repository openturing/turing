turingApp.factory('turMLDataGroupDataResource', [
		'$resource', 'turAPIServerService',
		function($resource, turAPIServerService) {
			return $resource(
					turAPIServerService.get().concat('/ml/data/group/:dataGroupId/data/:id'), {
						id : '@id',
						dataGroupId : '@dataGroupId'
					}, {
						update : {
							method : 'PUT'
						}
					});
		} ]);