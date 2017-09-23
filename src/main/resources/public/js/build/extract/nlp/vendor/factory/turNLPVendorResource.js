turingApp.factory('turNLPVendorResource', [ '$resource', function($resource) {
	return $resource('/turing/api/nlp/vendor/:id', {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);