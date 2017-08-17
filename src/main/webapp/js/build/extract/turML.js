turingApp.factory('turMLInstanceResource', [ '$resource', function($resource) {
	return $resource('/turing/api/ml/:id', {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);

turingApp.factory('turMLVendorResource', [ '$resource', function($resource) {
	return $resource('/turing/api/ml/vendor/:id', {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);

turingApp.factory('turMLDataGroupResource', [ '$resource', function($resource) {
	return $resource('/turing/api/ml/data/group/:id', {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);

turingApp.factory('turMLModelResource', [ '$resource', function($resource) {
	return $resource('/turing/api/ml/model/:id', {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);

turingApp.controller('TurMLModelCtrl', [
		"$scope",
		"$http",
		"$window",
		"$state",
		"$rootScope",
		"$translate",
		"turMLModelResource",
		function($scope, $http, $window, $state, $rootScope, $translate,
				turMLModelResource) {
			$rootScope.$state = $state;
			$scope.mlModels = turMLModelResource.query();
		} ]);

turingApp.controller('TurMLDataGroupCtrl', [
		"$scope",
		"$http",
		"$window",
		"$state",
		"$rootScope",
		"$translate",
		"turMLDataGroupResource",
		function($scope, $http, $window, $state, $rootScope, $translate,
				turMLDataGroupResource) {
			$rootScope.$state = $state;
			$scope.mlDataGroups = turMLDataGroupResource.query();
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
		"turMLDataGroupResource",
		function($scope, $http, $window, $stateParams, $state, $rootScope,
				$translate, vigLocale, turMLDataGroupResource) {
			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);
			$rootScope.$state = $state;
			$scope.mlDataGroupId = $stateParams.mlDataGroupId;
			$scope.dataGroup = {};
			$scope.dataGroupSave = function() {
				turMLDataGroupResource.save($scope.dataGroup, function() {
					$state.go('ml.datagroup');
				});
			}
		} ]);

turingApp.controller('TurMLDataGroupEditCtrl', [
		"$scope",	
		"$stateParams",
		"$state",
		"$rootScope",
		"$translate",
		"vigLocale",
		"turMLDataGroupResource",
		function($scope, $stateParams, $state, $rootScope,
				$translate, vigLocale, turMLDataGroupResource) {
			
			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);
			$rootScope.$state = $state;
			
			$scope.dataGroup = turMLDataGroupResource.get({
				id : $stateParams.mlDataGroupId
			});
			$scope.dataGroupSave = function() {
				$scope.dataGroup.$update(function() {
					$state.go('ml.datagroup');
				});
			}
			$scope.dataGroupDelete = function() {
				$scope.dataGroup.$delete(function() {
					$state.go('ml.datagroup');
				});
			}
		} ]);
turingApp.controller('TurMLInstanceCtrl', [
		"$scope",
		"$state",
		"$rootScope",
		"$translate",
		"turMLInstanceResource",
		function($scope, $state, $rootScope, $translate,
				turMLInstanceResource) {
			
			$rootScope.$state = $state;
			$scope.mls = turMLInstanceResource.query();
		} ]);

turingApp.controller('TurMLInstanceNewCtrl', [
	"$scope",
	"$state",
	"$rootScope",
	"$translate",
	"vigLocale",
	"turMLInstanceResource",
	"turMLVendorResource",
	function($scope, $state, $rootScope, $translate, vigLocale,
			turMLInstanceResource, turMLVendorResource) {

		$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
		$translate.use($scope.vigLanguage);

		$rootScope.$state = $state;
		$scope.mlVendors = turMLVendorResource.query();
		$scope.ml = {'enabled': 0, 'selected': 0};
		$scope.mlInstanceSave = function() {
			turMLInstanceResource.save($scope.ml, function() {
				$state.go('ml.instance');
			});
		}
	} ]);

turingApp.controller('TurMLInstanceEditCtrl', [
		"$scope",		
		"$stateParams",
		"$state",
		"$rootScope",
		"$translate",
		"vigLocale",
		"turMLInstanceResource",
		"turMLVendorResource",
		function($scope, $stateParams, $state, $rootScope,
				$translate, vigLocale, turMLInstanceResource,
				turMLVendorResource) {
			
			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);
			$rootScope.$state = $state;
			
			$scope.mlVendors = turMLVendorResource.query();
			$scope.ml = turMLInstanceResource.get({
				id : $stateParams.mlInstanceId
			});
			
			$scope.mlInstanceUpdate = function() {
				$scope.ml.$update(function() {
					$state.go('ml.instance');
				});
			}
			$scope.mlInstanceDelete = function() {
				$scope.ml.$delete(function() {
					$state.go('ml.instance');
				});
			}
		} ]);