turingApp.factory('turLocaleResource', [ '$resource', function($resource) {
	return $resource($turServer.concat('/locale/:id'), {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);