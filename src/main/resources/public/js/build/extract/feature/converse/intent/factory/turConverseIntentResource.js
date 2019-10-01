turingApp.factory('turConverseIntentResource', ['$resource', 'turAPIServerService', function ($resource, turAPIServerService) {
	return $resource(turAPIServerService.get().concat('/converse/intent/:id'), {
		id: '@id'
	}, {
		update: {
			method: 'PUT'
		}
	});
}]);