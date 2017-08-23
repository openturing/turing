turingApp.factory('turMLCategoryResource', [ '$resource', function($resource) {
	return $resource('/turing/api/ml/category/:id', {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);