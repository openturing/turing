turingApp.controller('TurRoleNewCtrl', [
	"$scope",
	"$http",
	"$state",
	"$rootScope",
	"turRoleFactory",
	"turAPIServerService",
	function($scope, $http, $state, $rootScope,
			turRoleFactory, turAPIServerService) {
		$rootScope.$state = $state;
		$scope.role = {};
		$scope.$evalAsync($http.get(
				turAPIServerService.get().concat("/v2/role/model")).then(
				function(response) {
					$scope.role = response.data;
				}));
		$scope.roleSave = function() {
			turRoleFactory.save($scope.role);
		}
	} ]);