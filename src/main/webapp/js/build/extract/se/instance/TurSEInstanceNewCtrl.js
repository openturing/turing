turingApp.controller('TurSEInstanceNewCtrl', [
		"$scope",
		"$state",
		"$rootScope",
		"$translate",
		"vigLocale",
		"turSEInstanceResource",
		"turSEVendorResource",
		"turLocaleResource",
		function($scope, $state, $rootScope, $translate, vigLocale,
				turSEInstanceResource, turSEVendorResource, turLocaleResource) {

			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);

			$rootScope.$state = $state;
			$scope.locales = turLocaleResource.query();
			$scope.seVendors = turSEVendorResource.query();
			$scope.se = {
				'enabled' : 0
			};
			$scope.seInstanceSave = function() {
				turSEInstanceResource.save($scope.se, function() {
					$state.go('se.instance');
				});
			}
		} ]);