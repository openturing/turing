turingApp.factory('turMLVendorResource', [ '$resource', 'turAPIServerService', function($resource, turAPIServerService) {
	return $resource(turAPIServerService.get().concat('/ml/vendor/:id'), {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);