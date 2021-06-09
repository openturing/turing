turingApp.controller('TurConverseAgentListCtrl', [
	"$scope",
	"$state",
	"$rootScope",
	"turConverseAgentResource",
	function ($scope, $state, $rootScope,
		turConverseAgentResource) {
		$rootScope.$state = $state;
		$scope.agents = turConverseAgentResource.query();
	}]);