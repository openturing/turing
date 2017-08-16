turingApp.factory('turSEInstanceResource', [ '$resource', function($resource) {
	return $resource('/turing/api/se/:id');
} ]);

turingApp.factory('turSEVendorResource', [ '$resource', function($resource) {
	return $resource('/turing/api/se/vendor/:id');
} ]);

turingApp.factory('turSESNResource', [ '$resource', function($resource) {
	return $resource('/turing/api/se/sn/:id');
} ]);

turingApp.controller('TurSEInstanceCtrl', [
		"$scope",
		"$http",
		"$window",
		"$state",
		"$rootScope",
		"$translate",
		"turSEInstanceResource",
		function($scope, $http, $window, $state, $rootScope, $translate,
				turSEInstanceResource) {
			$rootScope.$state = $state;
			$scope.ses = turSEInstanceResource.query();
		} ]);

turingApp.controller('TurSEInstanceEditCtrl', [
		"$scope",
		"$http",
		"$window",
		"$stateParams",
		"$state",
		"$rootScope",
		"$translate",
		"vigLocale",
		"turSEInstanceResource",
		"turSEVendorResource",
		function($scope, $http, $window, $stateParams, $state, $rootScope,
				$translate, vigLocale, turSEInstanceResource,
				turSEVendorResource) {
			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);
			$rootScope.$state = $state;
			$scope.seInstanceId = $stateParams.seInstanceId;
			$scope.seVendors = turSEVendorResource.query();
			$scope.se = turSEInstanceResource.get({
				id : $scope.seInstanceId
			});
		} ]);
turingApp.controller('TurSESNCtrl', [
		"$scope",
		"$http",
		"$window",
		"$state",
		"$rootScope",
		"$translate",
		"turSESNResource",
		function($scope, $http, $window, $state, $rootScope, $translate,
				turSESNResource) {
			$rootScope.$state = $state;
			$scope.seSNs = turSESNResource.query();
		} ]);
