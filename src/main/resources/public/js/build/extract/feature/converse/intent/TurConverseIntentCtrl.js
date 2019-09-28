turingApp.controller('TurConverseIntentCtrl', [
	"$scope",
	"$http",
	"$window",
	"$state",
	"$rootScope",
	"$translate",
	"turConverseIntentResource",
	"$stateParams",
	function ($scope, $http, $window, $state, $rootScope, $translate, turConverseIntentResource, $stateParams) {
		$rootScope.$state = $state;
		$scope.phraseText = "";
		$scope.intentId = $stateParams.intentId;
		$scope.intent = turConverseIntentResource.get({
			id: $scope.intentId
		});

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
			turConverseIntentResource.update({ id: $scope.intent.id }, $scope.intent, function (response) {
				console.log("Save Intent");
			});
		}

		$scope.addPhrase = function (phraseText) {
			var phraseObject = {};
			phraseObject.text = phraseText;
			$scope.intent.phrases.unshift(phraseObject);
			phraseText = "";
		}

		$scope.removePhrase = function (index) {
			$scope.intent.phrases.splice(index, 1);
		}
		
		$scope.addResponse = function (responseText) {
			var responseObject = {};
			responseObject.text = responseText;
			$scope.intent.responses.unshift(responseObject);
			responseText = "";
		}

		$scope.removeResponse = function (index) {
			$scope.intent.responses.splice(index, 1);
		}

	}]);