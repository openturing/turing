turingApp.factory('turSNSiteResource', [ '$resource', function($resource) {
	return $resource('/turing/api/sn/:id', {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);