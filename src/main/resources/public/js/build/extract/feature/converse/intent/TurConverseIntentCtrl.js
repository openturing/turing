turingApp.controller('TurConverseIntentCtrl', [
	"$scope",
	"$http",
	"$window",
	"$state",
	"$rootScope",
	"$translate",
	"turConverseIntentResource",
	"$stateParams",
	"turAPIServerService",
	"$filter",
	"turConverseIntentFactory",
	"Notification",
	function ($scope, $http, $window, $state, $rootScope, $translate, turConverseIntentResource, $stateParams, turAPIServerService, $filter, turConverseIntentFactory, Notification) {
		$rootScope.$state = $state;
		$scope.form = {
			phraseText: "",
			responseText: ""
		}
		$scope.contexts = [];
		$scope.contextObjects = [];
		$scope.object = {
			contextInputs: [],
			contextOutputs: []
		}
		$scope
			.$evalAsync($http
				.get(turAPIServerService.get().concat("/converse/agent/" + $scope.agentId + "/contexts"))
				.then(
					function (response) {
						$scope.contextObjects = response.data;
						angular.forEach($scope.contextObjects, function (context, key) {
							$scope.contexts.push(context.text);
						});
					}));

		$scope.intentId = $stateParams.intentId;
		$scope.isNew = false;
		if ($scope.intentId !== null && typeof $scope.intentId !== 'undefined') {
			$scope.intent = turConverseIntentResource.get({
				id: $scope.intentId
			}, function () {
				angular.forEach($scope.intent.contextInputs, function (context, key) {
					$scope.object.contextInputs.push(context.text);
				});
				angular.forEach($scope.intent.contextOutputs, function (context, key) {
					$scope.object.contextOutputs.push(context.text);
				});

				$scope.intent.parameters = $filter('orderBy')($scope.intent.parameters, 'position');
			});
		}
		else {
			$scope.isNew = true;
			$scope
				.$evalAsync($http
					.get(turAPIServerService.get().concat("/converse/intent/model"))
					.then(
						function (response) {
							$scope.intent = response.data;
							$scope.intent.name = "Untitled Intent"
						}));

		}

		$scope.saveIntent = function () {

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
				turConverseIntentResource.save($scope.intent, function (response) {
					$scope.intent = response;
					$scope.intent.parameters = $filter('orderBy')($scope.intent.parameters, 'position');
					$scope.isNew = false;
					$scope.intentId = response.id;
					Notification.warning($scope.intent.name + ' Intent was saved.');
				});
			}
			else {
				turConverseIntentResource.update({ id: $scope.intent.id }, $scope.intent, function (response) {
					$scope.intent = response;
					$scope.intent.parameters = $filter('orderBy')($scope.intent.parameters, 'position');
					Notification.warning($scope.intent.name + ' Intent was updated.');
				});
			}
		}

		$scope.addPhrase = function (phraseText) {
			var phraseObject = {};
			phraseObject.text = phraseText;
			$scope.intent.phrases.unshift(phraseObject);
			$scope.form.phraseText = "";
		}

		$scope.addParameter = function () {
			var parameterObject = {};
			parameterObject.required = false;
			parameterObject.name = null;
			parameterObject.entity = null;
			parameterObject.value = null;
			parameterObject.prompts = [];
			$scope.intent.parameters.push(parameterObject);

		}

		$scope.removeParameter = function (index) {
			$scope.intent.parameters.splice(index, 1);
		}
		$scope.removePhrase = function (index) {
			$scope.intent.phrases.splice(index, 1);
		}

		$scope.addResponse = function (responseText) {
			var responseObject = {};
			responseObject.text = responseText;
			$scope.intent.responses.unshift(responseObject);
			responseText = "";
			$scope.form.responseText = "";
		}

		$scope.removeResponse = function (index) {
			$scope.intent.responses.splice(index, 1);
		}

		$scope.editParamPrompts = function (actionName, parameter) {
			turConverseIntentFactory.showParamPrompts(actionName, parameter);
		}
	}]);