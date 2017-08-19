turingApp.controller('TurMLInstanceNewCtrl', [
		"$scope",
		"$state",
		"$rootScope",
		"$translate",
		"vigLocale",
		"turMLInstanceResource",
		"turMLVendorResource",
		"turLocaleResource",
		function($scope, $state, $rootScope, $translate, vigLocale,
				turMLInstanceResource, turMLVendorResource, turLocaleResource) {

			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);

			$rootScope.$state = $state;
			$scope.locales = turLocaleResource.query();
			$scope.mlVendors = turMLVendorResource.query();
			$scope.ml = {
				'enabled' : 0
			};
			$scope.mlInstanceSave = function() {
				turMLInstanceResource.save($scope.ml, function() {
					$state.go('ml.instance');
				});
			}
		} ]);