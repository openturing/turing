turingApp.controller('TurConverseAgentDetailCtrl', [
	"$scope",
	"$stateParams",
	"$state",
	"$rootScope",
	"$translate",
	"$http",
	"vigLocale",
	"turAPIServerService",
	"turSEInstanceResource",
	"turLocaleResource",
	"turConverseAgentResource",
	function ($scope, $stateParams, $state, $rootScope, $translate, $http, vigLocale, turAPIServerService, turSEInstanceResource, turLocaleResource, turConverseAgentResource) {
		$rootScope.$state = $state;
		$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
		$translate.use($scope.vigLanguage);
		$scope.agentId = $stateParams.agentId;
		$scope.seInstances = turSEInstanceResource.query();
		$scope.locales = turLocaleResource.query();

		$scope.isNew = false;
		if ($scope.agentId !== null && typeof $scope.agentId !== 'undefined') {
			$scope.agent = turConverseAgentResource.get({
				id: $scope.agentId
			});
		}
		else {
			$scope.isNew = true;
			$scope
				.$evalAsync($http
					.get(turAPIServerService.get().concat("/converse/agent/model"))
					.then(
						function (response) {
							$scope.agent = response.data;
							$scope.agent.name = "Untitled Agent"
						}));

		}

		$scope.agentSave = function () {
			if ($scope.isNew) {
				turConverseAgentResource.save($scope.agent, function (response) {
					$scope.agent = response;
					$scope.isNew = false;
					$scope.agentId = response.id;
					console.log("Save Agent");
					$state.go('converse.agent.intent', { agentId: response.id });

				});
			}
			else {
				turConverseAgentResource.update({ id: $scope.agent.id }, $scope.agent, function (response) {
					$scope.agent = response;
					console.log("Updated Agent");
				});
			}
		}

		$scope.agentDelete = function () {
			turConverseAgentResource.delete({ id: $scope.agent.id }, function (response) {
				$state.go('converse.agent-list');
			});
		}
	}]);