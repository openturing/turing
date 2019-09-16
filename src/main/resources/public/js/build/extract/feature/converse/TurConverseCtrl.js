turingApp.controller('TurConverseCtrl', [
	"$scope",
	"$http",
	"$window",
	"$state",
	"$rootScope",
	"$translate",
	"turAPIServerService",
	function ($scope, $http, $window, $state, $rootScope, $translate, turAPIServerService) {
		$rootScope.$state = $state;
		$scope.tryText = "";
		$scope.agentResponse = null;
		$scope.try = function () {
			$scope
				.$evalAsync($http
					.get(turAPIServerService.get().concat("/converse/agent/try?q=" + $scope.tryText))
					.then(
						function (response) {
							$scope.agentResponse = response.data;
						}));

		}
	}]);