turingApp.factory('turConverseTrainingResource', ['$resource', 'turAPIServerService', function ($resource, turAPIServerService) {
	return $resource(turAPIServerService.get().concat('/converse/training/:id'), {
		id: '@id'
	}, {
		update: {
			method: 'PUT'
		}
	});
}]);