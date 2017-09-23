turingApp.factory('turMLDataGroupDataResource', [
		'$resource',
		function($resource) {
			return $resource(
					'/turing/api/ml/data/group/:dataGroupId/data/:id', {
						id : '@id',
						dataGroupId : '@dataGroupId'
					}, {
						update : {
							method : 'PUT'
						}
					});
		} ]);