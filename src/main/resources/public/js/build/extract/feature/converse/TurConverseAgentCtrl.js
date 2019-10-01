turingApp.controller('TurConverseAgentCtrl', [
	"$scope",
	"$http",
	"$window",
	"$state",
	"$stateParams",
	"$rootScope",
	"$translate",
	"turAPIServerService",
	"turConverseAgentResource",
	function ($scope, $http, $window, $state, $stateParams, $rootScope, $translate, turAPIServerService, turConverseAgentResource) {
		$rootScope.$state = $state;
		$scope.agentId = $stateParams.agentId;
		$scope.tryText = "";
		$scope.agentResponse = null;
		$scope.agent = turConverseAgentResource.get({
			id: $scope.agentId
		});
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