turingApp.factory('turSEInstanceResource', [ '$resource', function($resource) {
	return $resource('/turing/api/se/:id', {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);

turingApp.factory('turSEVendorResource', [ '$resource', function($resource) {
	return $resource('/turing/api/se/vendor/:id', {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);

turingApp.factory('turSESNResource', [ '$resource', function($resource) {
	return $resource('/turing/api/se/sn/:id', {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
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

turingApp.controller('TurSEInstanceNewCtrl', [
	"$scope",
	"$state",
	"$rootScope",
	"$translate",
	"vigLocale",
	"turSEInstanceResource",
	"turSEVendorResource",
	function($scope, $state, $rootScope, $translate, vigLocale,
			turSEInstanceResource, turSEVendorResource) {

		$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
		$translate.use($scope.vigLanguage);

		$rootScope.$state = $state;
		$scope.seVendors = turSEVendorResource.query();
		$scope.se = {'enabled': 0, 'selected': 0};
		$scope.seInstanceSave = function() {
			turSEInstanceResource.save($scope.se, function() {
				$state.go('se.instance');
			});
		}
	} ]);

turingApp.controller('TurSEInstanceEditCtrl', [
		"$scope",
		"$stateParams",
		"$state",
		"$rootScope",
		"$translate",
		"vigLocale",
		"turSEInstanceResource",
		"turSEVendorResource",
		function($scope, $stateParams, $state, $rootScope,
				$translate, vigLocale, turSEInstanceResource,
				turSEVendorResource) {
			
			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);
			$rootScope.$state = $state;

			$scope.seVendors = turSEVendorResource.query();
			$scope.se = turSEInstanceResource.get({
				id : $stateParams.seInstanceId
			});
			
			$scope.seInstanceUpdate = function() {
				$scope.se.$update(function() {
					$state.go('se.instance');
				});
			}
			$scope.seInstanceDelete = function() {
				$scope.se.$delete(function() {
					$state.go('se.instance');
				});
			}
			
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
