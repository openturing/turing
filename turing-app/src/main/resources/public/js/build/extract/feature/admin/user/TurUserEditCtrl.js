turingApp.controller('TurUserEditCtrl', [
	"$scope",
	"$http",
	"$window",
	"$stateParams",
	"$state",
	"$rootScope",
	"$translate",
	"vigLocale",
	"turUserResource",
	"turUserFactory",
	function ($scope, $http, $window, $stateParams, $state, $rootScope, $translate, vigLocale, turUserResource, turUserFactory) {
		$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
		$translate.use($scope.vigLanguage);
		$rootScope.$state = $state;
		$scope.userId = $stateParams.userId;

		$scope.user = turUserResource.get({ id: $stateParams.userId });

		$scope.userSave = function () {
			turUserFactory.save($scope.user, false);
		}

		$scope.addGroups = function () {
			turUserFactory.addGroups($scope.user);
		}

		$scope.removeGroup = function (index) {
			$scope.user.turGroups.splice(index, 1);
		}
	}
]);