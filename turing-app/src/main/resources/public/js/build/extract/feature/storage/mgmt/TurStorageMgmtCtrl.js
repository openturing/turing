turingApp.controller('TurStorageMgmtCtrl', [
		"$scope",
		"$http",
		"$window",
		"$state",
		"$rootScope",
		"$translate",
		"turStorageMgmtResource",
		"$stateParams",
		function($scope, $http, $window, $state, $rootScope, $translate,
				turStorageMgmtResource, $stateParams) {
			$rootScope.$state = $state;
			$scope.currPath = $stateParams.path + "\/";
			console.log("Teste1");
			console.log($state.params.path);
			$scope.getFullPath = function (path){
				return  $stateParams.path + "/" + path;
			}
			if ($stateParams.path.length <= 0) {
				$scope.rootPath = true;
				$scope.filesAndDirs = turStorageMgmtResource.query();
			} else {
				$scope.rootPath = false;
				$scope.filesAndDirs = turStorageMgmtResource.get({
					id : $stateParams.path
				});

			}
		} ]);