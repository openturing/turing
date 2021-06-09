turingApp.controller('TurRoleEditCtrl', [
	"$scope",
	"$state",
	"$rootScope",
	"turRoleResource",
	"$stateParams",
	"Notification",
	function ($scope, $state, $rootScope,
		turRoleResource, $stateParams, Notification) {
		$rootScope.$state = $state;
		$scope.roleId = $stateParams.roleId;

		$scope.role = turRoleResource.get({
			id: $scope.roleId
		});

		$scope.roleSave = function () {
			$scope.role.$update(function () {
				Notification.warning('The ' + $scope.role.name + ' Role was updated.');
			});
		}
	}]);