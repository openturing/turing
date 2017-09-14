turingApp.factory('turMLDataGroupSentenceResource', [
		'$resource',
		function($resource) {
			return $resource(
					'/turing/api/ml/data/group/:dataGroupId/sentence/:id', {
						id : '@id',
						dataGroupId : '@dataGroupId'
					}, {
						update : {
							method : 'PUT'
						}
					});
		} ]);