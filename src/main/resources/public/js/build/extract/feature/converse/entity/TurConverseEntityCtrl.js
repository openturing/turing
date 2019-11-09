turingApp.controller('TurConverseEntityCtrl', [
	"$scope",
	"$http",
	"$state",
	"$rootScope",
	"turConverseEntityResource",
	"$stateParams",
	"turAPIServerService",
	"$filter",
	"Notification",
	function ($scope, $http, $state, $rootScope, turConverseEntityResource, $stateParams, turAPIServerService, $filter, Notification) {
		$rootScope.$state = $state;

		$scope.entityId = $stateParams.entityId;
		$scope.terms = [];
		$scope.isNew = false;
		$scope.termModel = null;
			$scope
				.$evalAsync($http
					.get(turAPIServerService.get().concat("/converse/entity/term/model"))
					.then(
						function (response) {
							$scope.termModel = response.data;
						//	$scope.terms.push($scope.termModel);
						}));

		if ($scope.entityId !== null && typeof $scope.entityId !== 'undefined') {
			$scope.entity = turConverseEntityResource.get({
				id: $scope.entityId
			}, function(response) {
				//$scope.terms = response.terms;
			});
		}
		else {
			$scope.isNew = true;
			$scope
				.$evalAsync($http
					.get(turAPIServerService.get().concat("/converse/entity/model"))
					.then(
						function (response) {
							$scope.entity = response.data;
							$scope.entity.name = "Untitled Entity"
						}));

		}

		$scope.addRow = function () {
			console.log("Add Row");
			var newTerm = angular.copy($scope.termModel);
			newTerm.synonyms = [];
			$scope.entity.terms.push(newTerm);
		}
 		$scope.saveEntity = function () {
			if ($scope.isNew) {
				$scope.entity.agent = $scope.agentId;
				turConverseEntityResource.save($scope.entity, function (response) {
					$scope.entity = response;				
					$scope.isNew = false;
					Notification.warning($scope.entity.name + ' Entity was saved.');
				});
			}
			else {
				turConverseEntityResource.update({ id: $scope.entity.id }, $scope.entity, function (response) {
					$scope.entity = response;
					Notification.warning($scope.entity.name + ' Entity was updated.');
				});
			}
		}
	}]);