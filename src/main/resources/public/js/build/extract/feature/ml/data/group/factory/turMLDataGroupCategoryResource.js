turingApp.factory('turMLDataGroupCategoryResource', [
		'$resource',
		function($resource) {
			return $resource(
					'/turing/api/ml/data/group/:dataGroupId/category/:id', {
						id : '@id',
						dataGroupId : '@dataGroupId'
					}, {
						update : {
							method : 'PUT'
						}
					});
		} ]);