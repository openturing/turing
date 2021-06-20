turingApp.controller('TurUserCtrl', [
	"$scope",
	"$state",
	"$rootScope",
	"turUserResource",
	"turUserFactory",
	function ($scope, $state, $rootScope, turUserResource, turUserFactory) {
		$rootScope.$state = $state;
		$scope.users = null;
		$scope.users = turUserResource.query();

		$scope.userDelete = function (turUser) {
			turUserFactory.delete(turUser);
		}
	}]);
