turingApp.factory('turSNSiteFieldExtResource', [
		'$resource',
		'turAPIServerService',
		function($resource, turAPIServerService) {
			return $resource(turAPIServerService.get().concat(
					'/sn/:snSiteId/field/ext/:id'), {
				id : '@id',
				snSiteId : '@snSiteId'
			}, {
				update : {
					method : 'PUT'
				}
			});
		} ]);