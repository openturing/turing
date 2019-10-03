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
	function ($scope, $http, $window, $state, $rootScope, $translate, turConverseIntentResource, $stateParams, turAPIServerService, $filter) {
		$rootScope.$state = $state;
		$scope.form = {
			phraseText: "",
			responseText: ""
		}
		$scope.intentId = $stateParams.intentId;
		console.log($scope.intentId);
		$scope.isNew = false;
		if ($scope.intentId !== null && typeof $scope.intentId !== 'undefined') {
			$scope.intent = turConverseIntentResource.get({
				id: $scope.intentId
			}, function() {
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
			if (!angular.isArray($scope.intent.contextInputs)) {
				var contextInputsArray = [];
				contextInputsArray.push($scope.intent.contextInputs);
				$scope.intent.contextInputs = contextInputsArray;
			}
			if (!angular.isArray($scope.intent.contextOutputs)) {
				var contextOutputsArray = [];
				contextOutputsArray.push($scope.intent.contextOutputs);
				$scope.intent.contextOutputs = contextOutputsArray;
			}
			if ($scope.isNew) {
				$scope.intent.agent = $scope.agentId;
				turConverseIntentResource.save($scope.intent, function (response) {
					$scope.intent = response;
					$scope.intent.parameters = $filter('orderBy')($scope.intent.parameters, 'position');
					$scope.isNew = false;
					$scope.intentId = response.id;
					console.log("Save Intent");
				});
			}
			else {
				turConverseIntentResource.update({ id: $scope.intent.id }, $scope.intent, function (response) {
					$scope.intent = response;
					$scope.intent.parameters = $filter('orderBy')($scope.intent.parameters, 'position');
					console.log("Updated Intent");
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

	}]);