turingApp.controller('TurConverseEntityListCtrl', [
	"$scope",
	"$http",
	"$state",
	"$stateParams",
	"$rootScope",	
	"turConverseEntityResource",
	"turAPIServerService",
	"Notification",
	function ($scope, $http, $state, $stateParams, $rootScope, turConverseEntityResource, turAPIServerService, Notification) {
		$rootScope.$state = $state;
		$scope.agentId = $stateParams.agentId;
		$scope.entities = [];
		$scope
		.$evalAsync($http
			.get(turAPIServerService.get().concat("/converse/agent/" + $scope.agentId + "/entities"))
			.then(
				function (response) {
					$scope.entities = response.data;
				}));
		$scope.removeEntity = function (entity, index) {
			turConverseEntityResource.delete({ id: entity.id }, function () {
				$scope.entities.splice(index, 1);
				Notification.error(entity.name + " Entity was deleted");
			});
			

		}
	}]);