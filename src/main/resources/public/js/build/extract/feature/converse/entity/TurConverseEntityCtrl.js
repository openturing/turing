turingApp.controller('TurConverseEntityCtrl', [
	"$scope",
	"$http",
	"$state",
	"$rootScope",
	"turConverseEntityResource",
	"$stateParams",
	"turAPIServerService",
	"$filter",
	function ($scope, $http, $state, $rootScope, turConverseEntityResource, $stateParams, turAPIServerService, $filter) {
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
							$scope.terms.push($scope.termModel);
						}));

		if ($scope.entityId !== null && typeof $scope.entityId !== 'undefined') {
			$scope.entity = turConverseEntityResource.get({
				id: $scope.entityId
			}, function(response) {
				$scope.terms = response.terms;
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
			$scope.terms.push(angular.copy($scope.termModel));
		}
 		$scope.saveEntity = function () {

			var contextInputObjects = [];
			angular.forEach($scope.object.contextInputs, function (context, key) {
				var contextObject = $filter('filter')($scope.contextObjects, { text: context }, true)[0];
				if (contextObject === null || typeof contextObject === 'undefined') {
					contextObject = {
						text: context,
						agent: $scope.agentId
					}
				}				
				contextInputObjects.push(contextObject);
			});
			$scope.intent.contextInputs = contextInputObjects;

			var contextOutputObjects = [];
			angular.forEach($scope.object.contextOutputs, function (context, key) {
				var contextObject = $filter('filter')($scope.contextObjects, { text: context }, true)[0];
				if (contextObject === null || typeof contextObject === 'undefined') {
					contextObject = {
						text: context,
						agent: $scope.agentId
					}
				}
				contextOutputObjects.push(contextObject);
			});
			$scope.intent.contextOutputs = contextOutputObjects;

			if ($scope.isNew) {
				$scope.intent.agent = $scope.agentId;
				turConverseEntityResource.save($scope.intent, function (response) {
					$scope.intent = response;
					$scope.intent.parameters = $filter('orderBy')($scope.intent.parameters, 'position');
					$scope.isNew = false;
					$scope.intentId = response.id;
					console.log("Save Intent");
				});
			}
			else {
				turConverseEntityResource.update({ id: $scope.intent.id }, $scope.intent, function (response) {
					$scope.intent = response;
					$scope.intent.parameters = $filter('orderBy')($scope.intent.parameters, 'position');
					console.log("Updated Intent");
				});
			}
		}
	}]);