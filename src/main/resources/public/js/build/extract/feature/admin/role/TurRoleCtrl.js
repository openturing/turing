turingApp.controller('TurRoleCtrl', [
	"$scope",
	"$state",
	"$rootScope",
	"turRoleResource",
	"turRoleFactory",
	function ($scope, $state, $rootScope, turRoleResource, turRoleFactory) {
		$rootScope.$state = $state;
		$scope.roles = null;
		$scope.roles = turRoleResource.query();

		$scope.roleDelete = function (turRole) {
			turRoleFactory.delete(turRole);
		}
	}]);
