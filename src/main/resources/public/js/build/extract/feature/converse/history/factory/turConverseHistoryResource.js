turingApp.factory('turConverseHistoryResource', ['$resource', 'turAPIServerService', function ($resource, turAPIServerService) {
	return $resource(turAPIServerService.get().concat('/converse/history/:id'), {
		id: '@id'
	}, {
		update: {
			method: 'PUT'
		}
	});
}]);