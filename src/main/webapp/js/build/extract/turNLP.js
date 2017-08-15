turingApp.controller('TurNLPValidationCtrl', [
	"$scope",
	"$http",
	"$window",
	"$state",
	"$rootScope",
	"$translate",
	function ($scope, $http, $window, $state, $rootScope, $translate) {
		$scope.nlps = null;
		$scope.results = null;
		$scope.text = null;
		$scope.nlpmodel = null;
		$rootScope.$state = $state;
		$scope.$evalAsync($http.get(
			"/turing/api/nlp/").then(
			function (response) {
				$scope.nlps = response.data;
				angular.forEach(response.data, function(value, key) {
					if (value.selected == true) {
						$scope.nlpmodel = value.id;
					}
				});
			}));
		$scope.changeView = function(view) {
			
			postData = 'turText=' + $scope.text + "&turNLP=" + $scope.nlpmodel;
			$http({
				method : 'POST',
				url : '/turing/api/nlp/validate',
				data : postData, // forms user object
				headers : {
					'Content-Type' : 'application/x-www-form-urlencoded'
				}
			}).success(function(data, status, headers, config) {
				$scope.results = data;

			});

		};
	}]);

turingApp.controller('TurNLPInstanceCtrl', [
	"$scope",
	"$http",
	"$window",
	"$state",
	"$rootScope",
	"$translate",
	function ($scope, $http, $window, $state, $rootScope, $translate) {
		$scope.nlps = null;
		$rootScope.$state = $state;
		$scope.$evalAsync($http.get(
			"/turing/api/nlp/").then(
			function (response) {
				$scope.nlps = response.data;
			}));
	}]);

turingApp.controller('TurNLPEntityCtrl', [
	"$scope",
	"$http",
	"$window",
	"$state",
	"$rootScope",
	"$translate",
	function ($scope, $http, $window, $state, $rootScope, $translate) {
		$scope.entities = null;
		$rootScope.$state = $state;
		$scope.$evalAsync($http.get(
			"/turing/api/entity/").then(
			function (response) {
				$scope.entities = response.data;
			}));
	}]);

turingApp.controller('TurNLPInstanceEditCtrl', [
	"$scope",
	"$http",
	"$window",
	"$stateParams",
	"$state",
	"$rootScope",
	"$translate",
	"vigLocale",
	function ($scope, $http, $window, $stateParams, $state, $rootScope, $translate, vigLocale) {
		$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
		$translate.use($scope.vigLanguage);
		$rootScope.$state = $state;
		$scope.nlp = null;
		$scope.nlpVendors = null;
		$scope.nlpInstanceId = $stateParams.nlpInstanceId;
		$scope.$evalAsync($http.get(
		"/turing/api/nlp/vendor").then(
		function (response) {
			$scope.nlpVendors = response.data;
		}));
		$scope.$evalAsync($http.get(
			"/turing/api/nlp/" + $scope.nlpInstanceId).then(
			function (response) {
				$scope.nlp = response.data;
			}));
	
		$scope.mappingSave = function () {
			$scope.mappings = null;
			var parameter = JSON.stringify($scope.mapping);
			$http.put("../api/mapping/" + $scope.mappingId,
				parameter).then(
				function (data, status, headers, config) {
					   $state.go('mapping');
				}, function (data, status, headers, config) {
					   $state.go('mapping');
				});
		}
	}
]);

turingApp.controller('TurNLPEntityEditCtrl', [
	"$scope",
	"$http",
	"$window",
	"$stateParams",
	"$state",
	"$rootScope",
	"$translate",
	"vigLocale",
	function ($scope, $http, $window, $stateParams, $state, $rootScope, $translate, vigLocale) {
		$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
		$translate.use($scope.vigLanguage);
		$rootScope.$state = $state;
		$scope.entity = null;
		$scope.nlpEntityId = $stateParams.nlpEntityId;		
		$scope.$evalAsync($http.get(
			"/turing/api/entity/" + $scope.nlpEntityId).then(
			function (response) {
				$scope.entity = response.data;
			}));
	
		$scope.mappingSave = function () {
			$scope.mappings = null;
			var parameter = JSON.stringify($scope.mapping);
			$http.put("../api/mapping/" + $scope.mappingId,
				parameter).then(
				function (data, status, headers, config) {
					   $state.go('mapping');
				}, function (data, status, headers, config) {
					   $state.go('mapping');
				});
		}
	}
]);