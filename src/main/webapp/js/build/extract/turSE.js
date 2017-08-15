turingApp.controller('TurSEInstanceCtrl', [
	"$scope",
	"$http",
	"$window",
	"$state",
	"$rootScope",
	"$translate",
	function ($scope, $http, $window, $state, $rootScope, $translate) {
		$scope.ses = null;
		$rootScope.$state = $state;
		$scope.$evalAsync($http.get(
			"/turing/api/se").then(
			function (response) {
				$scope.ses = response.data;
			}));
	}]);

turingApp.controller('TurSEInstanceEditCtrl', [
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
		$scope.se = null;
		$scope.seVendors = null;
		$scope.seInstanceId = $stateParams.seInstanceId;
		$scope.$evalAsync($http.get(
		"/turing/api/se/vendor").then(
		function (response) {
			$scope.seVendors = response.data;
		}));
		$scope.$evalAsync($http.get(
			"/turing/api/se/" + $scope.seInstanceId).then(
			function (response) {
				$scope.se = response.data;
			}));
	}
]);
turingApp.controller('TurSESNCtrl', [
	"$scope",
	"$http",
	"$window",
	"$state",
	"$rootScope",
	"$translate",
	function ($scope, $http, $window, $state, $rootScope, $translate) {
		$scope.seSNs = null;
		$rootScope.$state = $state;
		$scope.$evalAsync($http.get(
			"/turing/api/se/sn").then(
			function (response) {
				$scope.seSNs = response.data;
			}));
	}]);
