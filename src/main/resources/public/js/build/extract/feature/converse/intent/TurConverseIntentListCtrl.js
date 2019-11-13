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
	"Notification",
	function ($scope, $http, $window, $state, $stateParams, $rootScope, $translate,turConverseIntentResource, turAPIServerService, Notification) {
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
			turConverseIntentResource.delete({ id: intent.id }, function() {
				$scope.conversation.responses.splice(index, 1);
				Notification.error(intent.name + " Intent was deleted");
			});
		}
	}]);