turingApp.factory('turMLDataSentenceResource', [
		'$resource',
		'turAPIServerService',
		function($resource, turAPIServerService) {
			return $resource(turAPIServerService.get().concat(
					'/ml/data/sentence/:id'), {
				id : '@id'
			}, {
				update : {
					method : 'PUT'
				}
			});
		} ]);