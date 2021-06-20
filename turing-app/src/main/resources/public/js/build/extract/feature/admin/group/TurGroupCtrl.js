turingApp.controller('TurGroupCtrl', [
	"$scope",
	"$state",
	"$rootScope",
	"turGroupResource",
	"turGroupFactory",
	function ($scope, $state, $rootScope, turGroupResource, turGroupFactory) {
		$rootScope.$state = $state;
		$scope.groups = null;
		$scope.groups = turGroupResource.query();

		$scope.groupDelete = function (turGroup) {
			turGroupFactory.delete(turGroup);
		}
	}]);
