turingApp.controller('TurMLInstanceNewCtrl', [
		"$scope",
		"$state",
		"$rootScope",
		"$translate",
		"vigLocale",
		"turMLInstanceResource",
		"turMLVendorResource",
		"turLocaleResource",
		"turNotificationService",
		function($scope, $state, $rootScope, $translate, vigLocale,
				turMLInstanceResource, turMLVendorResource, turLocaleResource, turNotificationService) {

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
					turNotificationService.addNotification("Machine Learning Instance \"" + $scope.ml.title + "\" was created.");
					$state.go('ml.instance');
				});
			}
		} ]);