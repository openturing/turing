turingApp.controller('TurMLModelCtrl', [
	"$scope",
	"$http",
	"$window",
	"$state",
	"$rootScope",
	"$translate",
	function ($scope, $http, $window, $state, $rootScope, $translate) {
		$scope.mlModels = null;
		$rootScope.$state = $state;
		$scope.$evalAsync($http.get(
			"/turing/api/ml/model").then(
			function (response) {
				$scope.mlModels = response.data;
			}));
	}]);

turingApp.controller('TurMLDataGroupCtrl', [
	"$scope",
	"$http",
	"$window",
	"$state",
	"$rootScope",
	"$translate",
	function ($scope, $http, $window, $state, $rootScope, $translate) {
		$scope.mlDataGroups = null;
		$rootScope.$state = $state;
		$scope.$evalAsync($http.get(
			"/turing/api/ml/data/group").then(
			function (response) {
				$scope.mlDataGroups = response.data;
			}));
	}]);

turingApp.controller('TurMLInstanceCtrl', [
	"$scope",
	"$http",
	"$window",
	"$state",
	"$rootScope",
	"$translate",
	function ($scope, $http, $window, $state, $rootScope, $translate) {
		$scope.mls = null;
		$rootScope.$state = $state;
		$scope.$evalAsync($http.get(
			"/turing/api/ml/").then(
			function (response) {
				$scope.mls = response.data;
			}));
	}]);

turingApp.controller('TurMLInstanceEditCtrl', [
	"$scope",
	"$http",
	"$window",
	"$stateParams",
	"$state",
	"$rootScope",
	"$translate",
	"vigLocale",
	function ($scope, $http, $window, $stateParams, $state, $rootScope, $translate, vigLocale) {
		$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
		$translate.use($scope.vigLanguage);
		$rootScope.$state = $state;
		$scope.ml = null;
		$scope.mlVendors = null;
		$scope.mlInstanceId = $stateParams.mlInstanceId;
		$scope.$evalAsync($http.get(
		"/turing/api/ml/vendor").then(
		function (response) {
			$scope.mlVendors = response.data;
		}));
		$scope.$evalAsync($http.get(
			"/turing/api/ml/" + $scope.mlInstanceId).then(
			function (response) {
				$scope.ml = response.data;
			}));
	}
]);