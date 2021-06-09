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
	"Notification",
	function ($scope, $stateParams, $state, $rootScope, $translate, $http, vigLocale, turAPIServerService, turSEInstanceResource, turLocaleResource, turConverseAgentResource, Notification) {
		$rootScope.$state = $state;
		$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
		$translate.use($scope.vigLanguage);
		$scope.agentId = $stateParams.agentId;
		$scope.seInstances = turSEInstanceResource.query();
		$scope.locales = turLocaleResource.query();

		$scope.isNew = false;
		if ($scope.agentId !== null && typeof $scope.agentId !== 'undefined') {
		//
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
					Notification.warning($scope.agent.name + ' Agent was saved.');
					$state.go('converse.agent.intent', { agentId: response.id });

				});
			}
			else {
				turConverseAgentResource.update({ id: $scope.agent.id }, $scope.agent, function (response) {
					$scope.agent = response;
					Notification.warning($scope.agent.name + ' Agent was updated.');
				});
			}
		}

		$scope.agentDelete = function () {
			turConverseAgentResource.delete({ id: $scope.agent.id }, function (response) {
				Notification.error($scope.agent.name + " Agent was deleted");
				$state.go('converse.agent-list');
			});
		}
	}]);