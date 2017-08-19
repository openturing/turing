turingApp.factory('turNLPInstanceResource', [ '$resource', function($resource) {
	return $resource('/turing/api/nlp/:id', {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);

turingApp.factory('turNLPVendorResource', [ '$resource', function($resource) {
	return $resource('/turing/api/nlp/vendor/:id', {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);

turingApp.factory('turNLPEntityResource', [ '$resource', function($resource) {
	return $resource('/turing/api/entity/:id', {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);

turingApp.controller('TurNLPValidationCtrl', [
		"$scope",
		"$http",
		"$window",
		"$state",
		"$rootScope",
		"$translate",
		"turNLPInstanceResource",
		function($scope, $http, $window, $state, $rootScope, $translate,
				turNLPInstanceResource) {
			$scope.results = null;
			$scope.text = null;
			$scope.nlpmodel = null;
			$rootScope.$state = $state;
			$scope.nlps = turNLPInstanceResource.query({}, function() {
				angular.forEach($scope.nlps, function(value, key) {
					if (value.selected == true) {
						$scope.nlpmodel = value.id;
					}
				})
			});
			$scope.changeView = function(view) {
				text = {
					'text' : $scope.text
				};
				var parameter = JSON.stringify(text);
				$http.post('/turing/api/nlp/' + $scope.nlpmodel + '/validate',
						parameter).then(function(response) {
					$scope.results = response.data;
				}, function(response) {
					//
				});
			};
		} ]);

turingApp.controller('TurNLPInstanceCtrl', [
		"$scope",
		"$http",
		"$window",
		"$state",
		"$rootScope",
		"$translate",
		"turNLPInstanceResource",
		function($scope, $http, $window, $state, $rootScope, $translate,
				turNLPInstanceResource) {
			$rootScope.$state = $state;
			$scope.nlps = turNLPInstanceResource.query();
		} ]);

turingApp.controller('TurNLPInstanceNewCtrl', [
		"$scope",
		"$state",
		"$rootScope",
		"$translate",
		"vigLocale",
		"turNLPInstanceResource",
		"turNLPVendorResource",
		"turLocaleResource",
		function($scope, $state, $rootScope, $translate, vigLocale,
				turNLPInstanceResource, turNLPVendorResource, turLocaleResource) {

			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);

			$rootScope.$state = $state;
			$scope.locales = turLocaleResource.query();
			$scope.nlpVendors = turNLPVendorResource.query();
			$scope.nlp = {
				'enabled' : 0				
			};
			$scope.nlpInstanceSave = function() {
				turNLPInstanceResource.save($scope.nlp, function() {
					$state.go('nlp.instance');
				});
			}
		} ]);

turingApp.controller('TurNLPInstanceEditCtrl', [
		"$scope",
		"$stateParams",
		"$state",
		"$rootScope",
		"$translate",
		"vigLocale",
		"turNLPInstanceResource",
		"turNLPVendorResource",
		"turLocaleResource",
		"$uibModal",
		function($scope, $stateParams, $state, $rootScope, $translate,
				vigLocale, turNLPInstanceResource, turNLPVendorResource,turLocaleResource,
				$uibModal) {

			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);

			$rootScope.$state = $state;
			$scope.locales = turLocaleResource.query();
			$scope.nlpVendors = turNLPVendorResource.query();
			$scope.nlp = turNLPInstanceResource.get({
				id : $stateParams.nlpInstanceId
			});

			$scope.nlpInstanceUpdate = function() {
				$scope.nlp.$update(function() {
					$state.go('nlp.instance');
				});
			}
			$scope.nlpInstanceDelete = function() {

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
							return $scope.nlp.title;
						}
					}
				});

				modalInstance.result.then(function(removeInstance) {
					$scope.removeInstance = removeInstance;
					$scope.nlp.$delete(function() {
						$state.go('nlp.instance');
					});
				}, function() {
					// Selected NO
				});
			}

		} ]);
turingApp.controller('TurNLPEntityCtrl', [
		"$scope",
		"$http",
		"$window",
		"$state",
		"$rootScope",
		"$translate",
		"turNLPEntityResource",
		function($scope, $http, $window, $state, $rootScope, $translate,
				turNLPEntityResource) {
			$rootScope.$state = $state;
			$scope.entities = turNLPEntityResource.query();
		} ]);

turingApp.controller('TurNLPEntityEditCtrl', [
		"$scope",
		"$http",
		"$window",
		"$stateParams",
		"$state",
		"$rootScope",
		"$translate",
		"vigLocale",
		"turNLPEntityResource",
		function($scope, $http, $window, $stateParams, $state, $rootScope,
				$translate, vigLocale, turNLPEntityResource) {
			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);
			$rootScope.$state = $state;
			$scope.nlpEntityId = $stateParams.nlpEntityId;
			$scope.entity = turNLPEntityResource.get({
				id : $scope.nlpEntityId
			});
		} ]);