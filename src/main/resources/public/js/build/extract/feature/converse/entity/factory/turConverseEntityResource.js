turingApp.factory('turConverseEntityResource', ['$resource', 'turAPIServerService', function ($resource, turAPIServerService) {
	return $resource(turAPIServerService.get().concat('/converse/entity/:id'), {
		id: '@id'
	}, {
		update: {
			method: 'PUT'
		}
	});
}]);