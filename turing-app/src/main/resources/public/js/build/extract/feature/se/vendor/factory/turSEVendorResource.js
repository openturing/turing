turingApp.factory('turSEVendorResource', [ '$resource', 'turAPIServerService', function($resource, turAPIServerService) {
	return $resource(turAPIServerService.get().concat('/se/vendor/:id'), {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);