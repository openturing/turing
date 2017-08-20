turingApp.controller('TurMLDataGroupNewCtrl', [
		"$scope",
		"$http",
		"$window",
		"$stateParams",
		"$state",
		"$rootScope",
		"$translate",
		"vigLocale",
		"turMLDataGroupResource",
		"turNotificationService",
		function($scope, $http, $window, $stateParams, $state, $rootScope,
				$translate, vigLocale, turMLDataGroupResource, turNotificationService) {
			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);
			$rootScope.$state = $state;
			$scope.mlDataGroupId = $stateParams.mlDataGroupId;
			$scope.dataGroup = {};
			$scope.dataGroupSave = function() {
				turMLDataGroupResource.save($scope.dataGroup, function() {
					turNotificationService.addNotification("Data Group \"" + $scope.dataGroup.name + "\" was created.");
					$state.go('ml.datagroup');
				});
			}
		} ]);