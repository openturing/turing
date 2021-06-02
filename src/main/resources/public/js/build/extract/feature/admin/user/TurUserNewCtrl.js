turingApp.controller('TurUserNewCtrl', [
		"$scope",
		"$http",
		"$state",
		"$rootScope",
		"turUserFactory",
		"turAPIServerService",
		function($scope, $http, $state, $rootScope,
				turUserFactory, turAPIServerService) {
			$rootScope.$state = $state;
			$scope.user = {};
			$scope.isNew = true;
			$scope.$evalAsync($http.get(
					turAPIServerService.get().concat("/v2/user/model")).then(
					function(response) {
						$scope.user = response.data;
					}));
			$scope.userSave = function() {
				turUserFactory.save($scope.user, $scope.isNew);
			}
		} ]);