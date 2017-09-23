turingApp.factory('turMLModelResource', [ '$resource', function($resource) {
	return $resource('/turing/api/ml/model/:id', {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);