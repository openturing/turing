turingApp.factory('turSNSiteResource', [ '$resource', function($resource) {
	return $resource('/turing/api/sn/:id', {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);

turingApp.controller('TurSNSiteCtrl', [
		"$scope",
		"$http",
		"$window",
		"$state",
		"$rootScope",
		"$translate",
		"turSNSiteResource",
		function($scope, $http, $window, $state, $rootScope, $translate,
				turSNSiteResource) {
			$rootScope.$state = $state;
			$scope.snSites = turSNSiteResource.query();
		} ]);

turingApp.controller('TurSNSiteNewCtrl', [
	"$scope",
	"$state",
	"$rootScope",
	"$translate",
	"vigLocale",
	"turSNSiteResource",
	"turSEInstanceResource",
	"turNLPInstanceResource",
	function($scope, $state, $rootScope, $translate, vigLocale,
			turSNSiteResource, turSEInstanceResource, turNLPInstanceResource) {

		$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
		$translate.use($scope.vigLanguage);

		$rootScope.$state = $state;
		$scope.seInstances = turSEInstanceResource.query();
		$scope.nlpInstances = turNLPInstanceResource.query();
		
		$scope.snSite = { };
		$scope.snSiteSave = function() {
			turSNSiteResource.save($scope.snSite, function() {
				$state.go('sn.site');
			});
		}
	} ]);

turingApp.controller('TurSNSiteEditCtrl', [
	"$scope",
	"$stateParams",
	"$state",
	"$rootScope",
	"$translate",
	"vigLocale",
	"turSNSiteResource",
	"turSEInstanceResource",
	"turNLPInstanceResource",
	"$uibModal",
	function($scope, $stateParams, $state, $rootScope, $translate,
			vigLocale, turSNSiteResource, turSEInstanceResource, turNLPInstanceResource, $uibModal) {

		$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
		$translate.use($scope.vigLanguage);
		$rootScope.$state = $state;

		$scope.seInstances = turSEInstanceResource.query();
		$scope.nlpInstances = turNLPInstanceResource.query();
		$scope.snSite = turSNSiteResource.get({
			id : $stateParams.snSiteId
		});

		$scope.snSiteUpdate = function() {
			$scope.snSite.$update(function() {
				$state.go('sn.site');
			});
		}
		$scope.snSiteDelete = function() {
			var $ctrl = this;

			var modalInstance = $uibModal.open({
				animation : true,
				ariaLabelledBy : 'modal-title',
				ariaDescribedBy : 'modal-body',
				templateUrl : 'templates/modal/turDeleteInstance.html',
				controller : 'ModalDeleteInstanceCtrl',
				controllerAs : '$ctrl',
				size : null,
				appendTo : undefined,
				resolve : {
					instanceName : function() {
						return $scope.snSite.name;
					}
				}
			});

			modalInstance.result.then(function(removeInstance) {
				$scope.removeInstance = removeInstance;
				$scope.snSite.$delete(function() {
					$state.go('sn.site');
				});
			}, function() {
				// Selected NO
			});

		}

	} ]);
