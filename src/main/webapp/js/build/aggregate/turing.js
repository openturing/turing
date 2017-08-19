var turingApp = angular.module('turingApp', [ 'ngResource', 'ngAnimate',
		'ngSanitize', 'ui.router', 'ui.bootstrap', 'pascalprecht.translate' ]);

turingApp.directive('convertToNumber', function() {
	return {
		require : 'ngModel',
		link : function(scope, element, attrs, ngModel) {
			ngModel.$parsers.push(function(val) {
				return parseInt(val, 10);
			});
			ngModel.$formatters.push(function(val) {
				return '' + val;
			});
		}
	};
});

turingApp.factory('vigLocale', [
		'$window',
		function($window) {
			return {
				getLocale : function() {
					var nav = $window.navigator;
					if (angular.isArray(nav.languages)) {
						if (nav.languages.length > 0) {
							return nav.languages[0].split('-').join('_');
						}
					}
					return ((nav.language || nav.browserLanguage
							|| nav.systemLanguage || nav.userLanguage) || '')
							.split('-').join('_');
				}
			}
		} ]);

turingApp.controller('TurHomeCtrl', [
	"$scope",
	"$http",
	"$window",
	"$state",
	"$rootScope",
	"$translate",
	function ($scope, $http, $window, $state, $rootScope, $translate) {
		$scope.accesses = null;
		$rootScope.$state = $state;		
	}]);
turingApp.controller('TurMLCategoryEditCtrl', [
		"$scope",
		"$stateParams",
		"$state",
		"$rootScope",
		"$translate",
		"vigLocale",
		"turMLDataGroupResource",
		"$uibModal",
		function($scope, $stateParams, $state, $rootScope, $translate,
				vigLocale, turMLDataGroupResource, $uibModal) {

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

			$scope.categoryNew = function() {
				var $ctrl = this;
				$scope.category = {
					'name' : 'cat01',
					'description' : 'description1'
				};
				var modalInstance = $uibModal.open({
					animation : true,
					ariaLabelledBy : 'modal-title',
					ariaDescribedBy : 'modal-body',
					templateUrl : 'templates/ml/category/ml-category-new.html',
					controller : 'TurMLCategoryNewCtrl',
					controllerAs : '$ctrl',
					size : null,
					appendTo : undefined,
					resolve : {
						category : function() {
							return $scope.category;
						}
					}
				});

				modalInstance.result.then(function(category) {
					console.log(category.name);
					console.log(category.description);
				}, function() {
					// Selected NO
				});

			}

		} ]);
turingApp.controller('TurMLCategoryNewCtrl', [ "$uibModalInstance",
	"category", function($uibModalInstance, category) {
		var $ctrl = this;
		$ctrl.removeInstance = false;
		$ctrl.category = category;
		$ctrl.ok = function() {
			$ctrl.removeInstance = true;
			$uibModalInstance.close(category);
		};

		$ctrl.cancel = function() {
			$ctrl.removeInstance = false;
			$uibModalInstance.dismiss('cancel');
		};
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
turingApp.controller('TurMLDataGroupEditCtrl', [
		"$scope",
		"$stateParams",
		"$state",
		"$rootScope",
		"$translate",
		"vigLocale",
		"turMLDataGroupResource",
		"$uibModal",
		function($scope, $stateParams, $state, $rootScope, $translate,
				vigLocale, turMLDataGroupResource, $uibModal) {

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
							return $scope.dataGroup.name;
						}
					}
				});

				modalInstance.result.then(function(removeInstance) {
					$scope.removeInstance = removeInstance;
					$scope.dataGroup.$delete(function() {
						$state.go('ml.datagroup');
					});
				}, function() {
					// Selected NO
				});

			}

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
turingApp.factory('turMLDataGroupResource', [ '$resource', function($resource) {
	return $resource('/turing/api/ml/data/group/:id', {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);
turingApp.controller('TurMLInstanceCtrl',
		[
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
turingApp.controller('TurMLInstanceEditCtrl', [
		"$scope",
		"$stateParams",
		"$state",
		"$rootScope",
		"$translate",
		"vigLocale",
		"turMLInstanceResource",
		"turMLVendorResource",
		"turLocaleResource",
		"$uibModal",
		function($scope, $stateParams, $state, $rootScope, $translate,
				vigLocale, turMLInstanceResource, turMLVendorResource,
				turLocaleResource, $uibModal) {

			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);
			$rootScope.$state = $state;

			$scope.locales = turLocaleResource.query();
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
							return $scope.ml.title;
						}
					}
				});

				modalInstance.result.then(function(removeInstance) {
					$scope.removeInstance = removeInstance;
					$scope.ml.$delete(function() {
						$state.go('ml.instance');
					});
				}, function() {
					// Selected NO
				});

			}
		} ]);

turingApp.controller('TurMLInstanceNewCtrl', [
		"$scope",
		"$state",
		"$rootScope",
		"$translate",
		"vigLocale",
		"turMLInstanceResource",
		"turMLVendorResource",
		"turLocaleResource",
		function($scope, $state, $rootScope, $translate, vigLocale,
				turMLInstanceResource, turMLVendorResource, turLocaleResource) {

			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);

			$rootScope.$state = $state;
			$scope.locales = turLocaleResource.query();
			$scope.mlVendors = turMLVendorResource.query();
			$scope.ml = {
				'enabled' : 0
			};
			$scope.mlInstanceSave = function() {
				turMLInstanceResource.save($scope.ml, function() {
					$state.go('ml.instance');
				});
			}
		} ]);
turingApp.factory('turMLInstanceResource', [ '$resource', function($resource) {
	return $resource('/turing/api/ml/:id', {
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
turingApp.factory('turMLModelResource', [ '$resource', function($resource) {
	return $resource('/turing/api/ml/model/:id', {
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
turingApp.factory('turNLPEntityResource', [ '$resource', function($resource) {
	return $resource('/turing/api/entity/:id', {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
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
turingApp.factory('turNLPInstanceResource', [ '$resource', function($resource) {
	return $resource('/turing/api/nlp/:id', {
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
turingApp.factory('turNLPVendorResource', [ '$resource', function($resource) {
	return $resource('/turing/api/nlp/vendor/:id', {
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
turingApp.controller('TurSEInstanceEditCtrl', [
		"$scope",
		"$stateParams",
		"$state",
		"$rootScope",
		"$translate",
		"vigLocale",
		"turSEInstanceResource",
		"turSEVendorResource",
		"turLocaleResource",
		"$uibModal",
		function($scope, $stateParams, $state, $rootScope, $translate,
				vigLocale, turSEInstanceResource, turSEVendorResource, turLocaleResource, $uibModal) {

			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);
			$rootScope.$state = $state;

			$scope.locales = turLocaleResource.query();
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
							return $scope.se.title;
						}
					}
				});

				modalInstance.result.then(function(removeInstance) {
					$scope.removeInstance = removeInstance;
					$scope.se.$delete(function() {
						$state.go('se.instance');
					});
				}, function() {
					// Selected NO
				});

			}

		} ]);

turingApp.controller('TurSEInstanceNewCtrl', [
		"$scope",
		"$state",
		"$rootScope",
		"$translate",
		"vigLocale",
		"turSEInstanceResource",
		"turSEVendorResource",
		"turLocaleResource",
		function($scope, $state, $rootScope, $translate, vigLocale,
				turSEInstanceResource, turSEVendorResource, turLocaleResource) {

			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);

			$rootScope.$state = $state;
			$scope.locales = turLocaleResource.query();
			$scope.seVendors = turSEVendorResource.query();
			$scope.se = {
				'enabled' : 0
			};
			$scope.seInstanceSave = function() {
				turSEInstanceResource.save($scope.se, function() {
					$state.go('se.instance');
				});
			}
		} ]);
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
turingApp.controller('TurSNAdvertisingCtrl', [
	"$scope",
	"$http",
	"$window",
	"$state",
	"$rootScope",
	"$translate",
	function($scope, $http, $window, $state, $rootScope, $translate) {
		$rootScope.$state = $state;
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
				vigLocale, turSNSiteResource, turSEInstanceResource,
				turNLPInstanceResource, $uibModal) {

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
				turSNSiteResource, turSEInstanceResource,
				turNLPInstanceResource) {

			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);

			$rootScope.$state = $state;
			$scope.snSite = {};

			$scope.seInstances = turSEInstanceResource.query({}, function() {
				angular.forEach($scope.seInstances, function(value, key) {
					if (value.selected == true) {
						value.title = value.title + " (Default)";
						$scope.snSite.turSEInstance = value;
					}
				})
			});

			$scope.nlpInstances = turNLPInstanceResource.query({}, function() {
				angular.forEach($scope.nlpInstances, function(value, key) {
					if (value.selected == true) {
						value.title = value.title + " (Default)";
						$scope.snSite.turNLPInstance = value;
					}
				})
			});

			$scope.snSiteSave = function() {
				turSNSiteResource.save($scope.snSite, function() {
					$state.go('sn.site');
				});
			}
		} ]);

turingApp.factory('turSNSiteResource', [ '$resource', function($resource) {
	return $resource('/turing/api/sn/:id', {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);
turingApp.controller('ModalDeleteInstanceCtrl', [ "$uibModalInstance",
		"instanceName", function($uibModalInstance, instanceName) {
			var $ctrl = this;
			$ctrl.removeInstance = false;
			$ctrl.instanceName = instanceName;
			$ctrl.ok = function() {
				$ctrl.removeInstance = true;
				$uibModalInstance.close($ctrl.removeInstance);
			};

			$ctrl.cancel = function() {
				$ctrl.removeInstance = false;
				$uibModalInstance.dismiss('cancel');
			};
		} ]);
turingApp.factory('turLocaleResource', [ '$resource', function($resource) {
	return $resource('/turing/api/locale/:id', {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);
turingApp.config([
		'$stateProvider',
		'$urlRouterProvider',
		'$locationProvider',
		'$translateProvider',
		function($stateProvider, $urlRouterProvider, $locationProvider,
				$translateProvider) {
			$translateProvider.useSanitizeValueStrategy('escaped');
			$translateProvider.translations('en', {

				NLP_EDIT : "Edit NLP",
				NLP_EDIT_SUBTITLE : "Change the NLP Settings",
				NAME : "Name",
				DESCRIPTION : "Description",
				VENDORS : "Vendors",
				HOST : "Host",
				PORT : "Port",
				SETTINGS_SAVE_CHANGES : "Save Changes"
			});
			$translateProvider.translations('pt', {
				NLP_EDIT : "Editar o NLP",
				NLP_EDIT_SUBTITLE : "Altere as configurações do NLP",
				NAME : "Nome",
				DESCRIPTION : "Descrição",
				VENDORS : "Produtos",
				HOST : "Host",
				PORT : "Porta",
				SETTINGS_SAVE_CHANGES : "Salvar Alterações"
			});
			$translateProvider.fallbackLanguage('en');

			$urlRouterProvider.otherwise('/home');
			$stateProvider.state('home', {
				url : '/home',
				templateUrl : 'templates/home.html',
				controller : 'TurHomeCtrl',
				data : {
					pageTitle : 'Home | Viglet Turing'
				}
			}).state('ml', {
				url : '/ml',
				templateUrl : 'templates/ml/ml.html',
				data : {
					pageTitle : 'Machine Learning | Viglet Turing'
				}
			}).state('ml.instance', {
				url : '/instance',
				templateUrl : 'templates/ml/ml-instance.html',
				controller : 'TurMLInstanceCtrl',
				data : {
					pageTitle : 'Machine Learnings | Viglet Turing'
				}
			}).state('ml.instance-new', {
				url : '/instance/new',
				templateUrl : 'templates/ml/ml-instance-new.html',
				controller : 'TurMLInstanceNewCtrl',
				data : {
					pageTitle : 'New Machine Learning Instance | Viglet Turing'
				}
			}).state('ml.instance-edit', {
				url : '/instance/:mlInstanceId',
				templateUrl : 'templates/ml/ml-instance-edit.html',
				controller : 'TurMLInstanceEditCtrl',
				data : {
					pageTitle : 'Edit Machine Learning | Viglet Turing'
				}
			}).state('ml.model', {
				url : '/model',
				templateUrl : 'templates/ml/model/ml-model.html',
				controller : 'TurMLModelCtrl',
				data : {
					pageTitle : 'Machine Learning Models | Viglet Turing'
				}
			}).state('ml.datagroup', {
				url : '/datagroup',
				templateUrl : 'templates/ml/data/group/ml-datagroup.html',
				controller : 'TurMLDataGroupCtrl',
				data : {
					pageTitle : 'Machine Learning Data Groups | Viglet Turing'
				}
			}).state('ml.datagroup-new', {
				url : '/datagroup/new',
				templateUrl : 'templates/ml/data/group/ml-datagroup-new.html',
				controller : 'TurMLDataGroupNewCtrl',
				data : {
					pageTitle : 'New Data Group | Viglet Turing'
				}
			}).state('ml.datagroup-edit', {
				url : '/datagroup/:mlDataGroupId',
				templateUrl : 'templates/ml/data/group/ml-datagroup-edit.html',
				controller : 'TurMLDataGroupEditCtrl',
				data : {
					pageTitle : 'Edit Data Group | Viglet Turing'
				}
			}).state('ml.datagroup-edit.category', {
				url : '/category',
				templateUrl : 'templates/ml/data/group/ml-datagroup-category.html',
				controller : 'TurMLCategoryEditCtrl',
				data : {
					pageTitle : 'Data Group Categories | Viglet Turing'
				}
			}).state('ml.datagroup-edit.data', {
				url : '/document',
				templateUrl : 'templates/ml/data/group/ml-datagroup-data.html',
				data : {
					pageTitle : 'Data Group Documents | Viglet Turing'
				}
			}).state('se', {
				url : '/se',
				templateUrl : 'templates/se/se.html',
				data : {
					pageTitle : 'Search Engine | Viglet Turing'
				}
			}).state('se.instance', {
				url : '/instance',
				templateUrl : 'templates/se/se-instance.html',
				controller : 'TurSEInstanceCtrl',
				data : {
					pageTitle : 'Search Engines | Viglet Turing'
				}
			}).state('se.instance-new', {
				url : '/instance/new',
				templateUrl : 'templates/se/se-instance-new.html',
				controller : 'TurSEInstanceNewCtrl',
				data : {
					pageTitle : 'New Search Engine Instance | Viglet Turing'
				}
			}).state('se.instance-edit', {
				url : '/instance/:seInstanceId',
				templateUrl : 'templates/se/se-instance-edit.html',
				controller : 'TurSEInstanceEditCtrl',
				data : {
					pageTitle : 'Edit Search Engine | Viglet Turing'
				}
			}).state('sn', {
				url : '/sn',
				templateUrl : 'templates/sn/sn.html',
				data : {
					pageTitle : 'Semantic Navigation | Viglet Turing'
				}
			}).state('sn.site', {
				url : '/site',
				templateUrl : 'templates/sn/sn-site.html',
				controller : 'TurSNSiteCtrl',
				data : {
					pageTitle : 'Semantic Navigation Sites | Viglet Turing'
				}
			}).state('sn.site-new', {
				url : '/site/new',
				templateUrl : 'templates/sn/sn-site-new.html',
				controller : 'TurSNSiteNewCtrl',
				data : {
					pageTitle : 'New Semantic Navigation Site | Viglet Turing'
				}
			}).state('sn.site-edit', {
				url : '/site/:snSiteId',
				templateUrl : 'templates/sn/sn-site-edit.html',
				controller : 'TurSNSiteEditCtrl',
				data : {
					pageTitle : 'Edit Semantic Navigation Site | Viglet Turing'
				}
			}).state('sn.ad', {
				url : '/ad',
				templateUrl : 'templates/sn/sn-ad.html',
				controller : 'TurSNAdvertisingCtrl',
				data : {
					pageTitle : 'Semantic Navigation Advertising | Viglet Turing'
				}
			}).state('nlp', {
				url : '/nlp',
				templateUrl : 'templates/nlp/nlp.html',
				data : {
					pageTitle : 'NLP | Viglet Turing'
				}
			}).state('nlp.instance', {
				url : '/instance',
				templateUrl : 'templates/nlp/nlp-instance.html',
				controller : 'TurNLPInstanceCtrl',
				data : {
					pageTitle : 'NLPs | Viglet Turing'
				}
			}).state('nlp.instance-new', {
				url : '/instance/new',
				templateUrl : 'templates/nlp/nlp-instance-new.html',
				controller : 'TurNLPInstanceNewCtrl',
				data : {
					pageTitle : 'New NLP Instance | Viglet Turing'
				}
			}).state('nlp.instance-edit', {
				url : '/instance/:nlpInstanceId',
				templateUrl : 'templates/nlp/nlp-instance-edit.html',
				controller : 'TurNLPInstanceEditCtrl',
				data : {
					pageTitle : 'Edit NLP | Viglet Turing'
				}
			}).state('nlp.validation', {
				url : '/validation',
				templateUrl : 'templates/nlp/nlp-validation.html',
				controller : 'TurNLPValidationCtrl',
				data : {
					pageTitle : 'NLP Validation | Viglet Turing'
				}
			}).state('nlp.entity', {
				url : '/entity',
				templateUrl : 'templates/nlp/entity/nlp-entity.html',
				controller : 'TurNLPEntityCtrl',
				data : {
					pageTitle : 'NLP Entities | Viglet Turing'
				}
			}).state('nlp.entity-import', {
				url : '/entity/import',
				templateUrl : 'templates/nlp/entity/nlp-entity-import.html',
				data : {
					pageTitle : 'Import Entity | Viglet Turing'
				}
			}).state('nlp.entity-edit', {
				url : '/entity/:nlpEntityId',
				templateUrl : 'templates/nlp/entity/nlp-entity-edit.html',
				controller : 'TurNLPEntityEditCtrl',
				data : {
					pageTitle : 'Edit Entity | Viglet Turing'
				}
			}).state('nlp.entity-edit.term', {
				url : '/term',
				templateUrl : 'templates/nlp/entity/nlp-entity-term.html',
				data : {
					pageTitle : 'Entity Terms | Viglet Turing'
				}
			});

		} ]);
