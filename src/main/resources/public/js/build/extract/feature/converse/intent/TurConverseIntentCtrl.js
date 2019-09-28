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
	}]);