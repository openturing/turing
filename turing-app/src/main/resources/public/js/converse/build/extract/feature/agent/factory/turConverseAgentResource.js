turConverseApp.factory('turConverseAgentResource', ['$resource', 'turAPIServerService', function ($resource, turAPIServerService) {
	return $resource(turAPIServerService.get().concat('/converse/agent/:id'), {
		id: '@id'
	}, {
		update: {
			method: 'PUT'
		}
	});
}]);