turingApp.factory('turMLDataGroupCategoryResource', [
		'$resource','turAPIServerService',
		function($resource, turAPIServerService) {
			return $resource(
					turAPIServerService.get().concat('/ml/data/group/:dataGroupId/category/:id'), {
						id : '@id',
						dataGroupId : '@dataGroupId'
					}, {
						update : {
							method : 'PUT'
						}
					});
		} ]);