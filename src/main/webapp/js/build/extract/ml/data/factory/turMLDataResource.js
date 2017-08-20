turingApp.factory('turMLDataResource', [ '$resource', function($resource) {
	return $resource('/turing/api/ml/data/:id', {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);