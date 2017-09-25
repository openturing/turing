turingApp.factory('turMLDataSentenceResource', [ '$resource', function($resource) {
	return $resource('/turing/api/ml/data/sentence/:id', {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);