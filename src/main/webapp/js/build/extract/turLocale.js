turingApp.factory('turLocaleResource', [ '$resource', function($resource) {
	return $resource('/turing/api/locale/:id', {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);