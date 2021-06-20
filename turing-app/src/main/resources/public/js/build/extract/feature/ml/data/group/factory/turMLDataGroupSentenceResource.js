turingApp.factory('turMLDataGroupSentenceResource', [
		'$resource', 'turAPIServerService',
		function($resource, turAPIServerService) {
			return $resource(
					turAPIServerService.get().concat('/ml/data/group/:dataGroupId/sentence/:id'), {
						id : '@id',
						dataGroupId : '@dataGroupId'
					}, {
						update : {
							method : 'PUT'
						}
					});
		} ]);