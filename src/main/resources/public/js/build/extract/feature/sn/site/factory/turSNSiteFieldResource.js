turingApp.factory('turSNSiteFieldResource', [ '$resource', 'turAPIServerService', function($resource, turAPIServerService) {
	return $resource(turAPIServerService.get().concat('/sn/:snSiteId/field/:id'), {
		id : '@id',
		snSiteId : '@snSiteId'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);