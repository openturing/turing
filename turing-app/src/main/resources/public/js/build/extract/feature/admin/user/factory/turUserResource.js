turingApp.factory('turUserResource', [ '$resource', 'turAPIServerService', function($resource, turAPIServerService) {
	return $resource(turAPIServerService.get().concat('/v2/user/:id'), {
		id : '@username'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);
