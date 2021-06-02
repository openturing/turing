turingApp.factory('turRoleResource', [ '$resource', 'turAPIServerService', function($resource, turAPIServerService) {
	return $resource(turAPIServerService.get().concat('/v2/role/:id'), {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);
