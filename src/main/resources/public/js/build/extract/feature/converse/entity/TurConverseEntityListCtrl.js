turingApp.controller('TurConverseEntityListCtrl', [
	"$scope",
	"$http",
	"$state",
	"$stateParams",
	"$rootScope",	
	"turConverseEntityResource",
	"turAPIServerService",
	function ($scope, $http, $state, $stateParams, $rootScope, turConverseEntityResource, turAPIServerService) {
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
			turConverseEntityResource.delete({ id: entity.id });
			$scope.entities.splice(index, 1);

		}
	}]);