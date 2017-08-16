turingApp.controller('TurMLModelCtrl', [
		"$scope",
		"$http",
		"$window",
		"$state",
		"$rootScope",
		"$translate",
		function($scope, $http, $window, $state, $rootScope, $translate) {
			$scope.mlModels = null;
			$rootScope.$state = $state;
			$scope.$evalAsync($http.get("/turing/api/ml/model").then(
					function(response) {
						$scope.mlModels = response.data;
					}));
		} ]);

turingApp.controller('TurMLDataGroupCtrl', [
		"$scope",
		"$http",
		"$window",
		"$state",
		"$rootScope",
		"$translate",
		function($scope, $http, $window, $state, $rootScope, $translate) {
			$scope.mlDataGroups = null;
			$rootScope.$state = $state;
			$scope.$evalAsync($http.get("/turing/api/ml/data/group").then(
					function(response) {
						$scope.mlDataGroups = response.data;
					}));
		} ]);

turingApp.controller('TurMLDataGroupNewCtrl', [
		"$scope",
		"$http",
		"$window",
		"$stateParams",
		"$state",
		"$rootScope",
		"$translate",
		"vigLocale",
		function($scope, $http, $window, $stateParams, $state, $rootScope,
				$translate, vigLocale) {
			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);
			$rootScope.$state = $state;
			$scope.mlDataGroupId = $stateParams.mlDataGroupId;
			$scope.dataGroup = {};
			$scope.dataGroupSave = function() {
				var parameter = JSON.stringify($scope.dataGroup);
				$http.post("/turing/api/ml/data/group/", parameter).then(
						function(data, status, headers, config) {
							$state.go('ml.datagroup');
						}, function(data, status, headers, config) {
							//
						});
			}
		} ]);

turingApp.controller('TurMLDataGroupEditCtrl', [
		"$scope",
		"$http",
		"$window",
		"$stateParams",
		"$state",
		"$rootScope",
		"$translate",
		"vigLocale",
		function($scope, $http, $window, $stateParams, $state, $rootScope,
				$translate, vigLocale) {
			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);
			$rootScope.$state = $state;
			$scope.dataGroup = null;
			$scope.mlDataGroupId = $stateParams.mlDataGroupId;
			$scope.$evalAsync($http.get(
					"/turing/api/ml/data/group/" + $scope.mlDataGroupId).then(
					function(response) {
						$scope.dataGroup = response.data;
					}));
			$scope.dataGroupSave = function() {
				var parameter = JSON.stringify($scope.dataGroup);
				$http.put("/turing/api/ml/data/group/" + $scope.mlDataGroupId,
						parameter).then(
						function(data, status, headers, config) {
							$state.go('ml.datagroup');
						}, function(data, status, headers, config) {
							//
						});
			}
			$scope.dataGroupDelete = function() {
				$http['delete'](
						"/turing/api/ml/data/group/" + $scope.mlDataGroupId)
						.then(function(data, status, headers, config) {
							$state.go('ml.datagroup');
						}, function(data, status, headers, config) {
							$state.go('ml.datagroup');
						});
			}
		} ]);
turingApp.controller('TurMLInstanceCtrl', [
		"$scope",
		"$http",
		"$window",
		"$state",
		"$rootScope",
		"$translate",
		function($scope, $http, $window, $state, $rootScope, $translate) {
			$scope.mls = null;
			$rootScope.$state = $state;
			$scope.$evalAsync($http.get("/turing/api/ml/").then(
					function(response) {
						$scope.mls = response.data;
					}));
		} ]);

turingApp.controller('TurMLInstanceEditCtrl', [
		"$scope",
		"$http",
		"$window",
		"$stateParams",
		"$state",
		"$rootScope",
		"$translate",
		"vigLocale",
		function($scope, $http, $window, $stateParams, $state, $rootScope,
				$translate, vigLocale) {
			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);
			$rootScope.$state = $state;
			$scope.ml = null;
			$scope.mlVendors = null;
			$scope.mlInstanceId = $stateParams.mlInstanceId;
			$scope.$evalAsync($http.get("/turing/api/ml/vendor").then(
					function(response) {
						$scope.mlVendors = response.data;
					}));
			$scope.$evalAsync($http
					.get("/turing/api/ml/" + $scope.mlInstanceId).then(
							function(response) {
								$scope.ml = response.data;
							}));
		} ]);