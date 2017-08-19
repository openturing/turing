turingApp.controller('TurSNSiteNewCtrl', [
		"$scope",
		"$state",
		"$rootScope",
		"$translate",
		"vigLocale",
		"turSNSiteResource",
		"turSEInstanceResource",
		"turNLPInstanceResource",
		function($scope, $state, $rootScope, $translate, vigLocale,
				turSNSiteResource, turSEInstanceResource,
				turNLPInstanceResource) {

			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);

			$rootScope.$state = $state;
			$scope.snSite = {};

			$scope.seInstances = turSEInstanceResource.query({}, function() {
				angular.forEach($scope.seInstances, function(value, key) {
					if (value.selected == true) {
						value.title = value.title + " (Default)";
						$scope.snSite.turSEInstance = value;
					}
				})
			});

			$scope.nlpInstances = turNLPInstanceResource.query({}, function() {
				angular.forEach($scope.nlpInstances, function(value, key) {
					if (value.selected == true) {
						value.title = value.title + " (Default)";
						$scope.snSite.turNLPInstance = value;
					}
				})
			});

			$scope.snSiteSave = function() {
				turSNSiteResource.save($scope.snSite, function() {
					$state.go('sn.site');
				});
			}
		} ]);
