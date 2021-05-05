turingApp.controller('TurGroupNewCtrl', [
	"$scope",
	"$http",
	"$state",
	"$rootScope",
	"turGroupFactory",
	"turAPIServerService",
	function($scope, $http, $state, $rootScope,
			turGroupFactory, turAPIServerService) {
		$rootScope.$state = $state;
		$scope.group = {};
		$scope.$evalAsync($http.get(
				turAPIServerService.get().concat("/v2/group/model")).then(
				function(response) {
					$scope.group = response.data;
				}));
		$scope.groupSave = function() {
			turGroupFactory.save($scope.group);
		}
	} ]);