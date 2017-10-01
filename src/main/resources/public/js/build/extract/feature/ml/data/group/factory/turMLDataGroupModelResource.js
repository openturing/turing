turingApp.factory('turMLDataGroupModelResource', [
		'$resource','turAPIServerService',
		function($resource, turAPIServerService) {
			return $resource(
					turAPIServerService.get().concat('/ml/data/group/:dataGroupId/model/:id'), {
						id : '@id',
						dataGroupId : '@dataGroupId'
					}, {
						update : {
							method : 'PUT'
						}
					});
		} ]);