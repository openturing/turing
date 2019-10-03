turingApp.controller('TurConverseIntentListCtrl', [
	"$scope",
	"$http",
	"$window",
	"$state",
	"$stateParams",
	"$rootScope",	
	"$translate",
	"turConverseIntentResource",
	"turAPIServerService",
	function ($scope, $http, $window, $state, $stateParams, $rootScope, $translate,turConverseIntentResource, turAPIServerService) {
		$rootScope.$state = $state;
		$scope.agentId = $stateParams.agentId;
		$scope.intents = [];
		$scope
		.$evalAsync($http
			.get(turAPIServerService.get().concat("/converse/agent/" + $scope.agentId + "/intents"))
			.then(
				function (response) {
					$scope.intents = response.data;
				}));
		$scope.removeIntent = function (intent, index) {
			turConverseIntentResource.delete({ id: intent.id });
			$scope.intents.splice(index, 1);

		}
	}]);