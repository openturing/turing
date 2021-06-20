turingApp.controller('TurGroupEditCtrl', [
	"$scope",
	"$state",
	"$rootScope",
	"turGroupResource",
	"turGroupFactory",
	"$stateParams",
	"Notification",
	function ($scope, $state, $rootScope,
		turGroupResource, turGroupFactory, $stateParams, Notification) {
		$rootScope.$state = $state;
		$scope.groupId = $stateParams.groupId;

		$scope.group = turGroupResource.get({
			id: $scope.groupId
		});

		$scope.groupSave = function () {
			angular.forEach($scope.group.turUsers, function (turUser, key) {
				console.log("removendo atributo: " + turUser.username);
				delete turUser.turGroups;									
			});
			$scope.group.$update(function () {
				Notification.warning('The ' + $scope.group.name + ' Group was updated.');
			});
		}

		$scope.addUsers = function () {
			turGroupFactory.addUsers($scope.group);
		}

		$scope.removeUser = function (index) {
			$scope.group.turUsers.splice(index, 1);
		}
	}]);