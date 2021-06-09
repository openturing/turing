turingApp.controller('TurNLPInstanceNewCtrl', [
		"$scope",
		"$state",
		"$rootScope",
		"$translate",
		"vigLocale",
		"turNLPInstanceResource",
		"turNLPVendorResource",
		"turLocaleResource",
		"turNotificationService",
		function($scope, $state, $rootScope, $translate, vigLocale,
				turNLPInstanceResource, turNLPVendorResource, turLocaleResource, turNotificationService) {

			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);

			$rootScope.$state = $state;
			$scope.locales = turLocaleResource.query();
			$scope.nlpVendors = turNLPVendorResource.query();
			$scope.nlp = {
				'enabled' : 0				
			};
			$scope.nlpInstanceSave = function() {
				turNLPInstanceResource.save($scope.nlp, function() {
					turNotificationService.addNotification("NLP Instance \"" + $scope.nlp.title + "\" was created.");
					$state.go('nlp.instance');
				});
			}
		} ]);