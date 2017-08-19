turingApp.factory('turNLPEntityResource', [ '$resource', function($resource) {
	return $resource('/turing/api/entity/:id', {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);
