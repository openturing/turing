var turingApp = angular.module('turingApp', ['ui.router', 'pascalprecht.translate']);

turingApp.factory('vigLocale', ['$window', function ($window) {
    return {
        getLocale: function () {
            var nav = $window.navigator;
            if (angular.isArray(nav.languages)) {
                if (nav.languages.length > 0) {
                    return nav.languages[0].split('-').join('_');
                }
            }
            return ((nav.language ||
                nav.browserLanguage ||
                nav.systemLanguage ||
                nav.userLanguage
            ) || '').split('-').join('_');
        }
    }
}]);
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
					pageTitle : 'Edit Data Group | Viglet Turing'
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
				data : {
					pageTitle : 'Data Group Categories | Viglet Turing'
				}
			}).state('ml.datagroup-edit.data', {
				url : '/documents',
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
			}).state('se.instance-edit', {
				url : '/instance/:seInstanceId',
				templateUrl : 'templates/se/se-instance-edit.html',
				controller : 'TurSEInstanceEditCtrl',
				data : {
					pageTitle : 'Edit Search Engine | Viglet Turing'
				}
			}).state('se.sn', {
				url : '/sn',
				templateUrl : 'templates/se/sn/se-sn.html',
				controller : 'TurSESNCtrl',
				data : {
					pageTitle : 'Semantic Navigation | Viglet Turing'
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
turingApp.controller('TurSEInstanceCtrl', [
	"$scope",
	"$http",
	"$window",
	"$state",
	"$rootScope",
	"$translate",
	function ($scope, $http, $window, $state, $rootScope, $translate) {
		$scope.ses = null;
		$rootScope.$state = $state;
		$scope.$evalAsync($http.get(
			"/turing/api/se").then(
			function (response) {
				$scope.ses = response.data;
			}));
	}]);

turingApp.controller('TurSEInstanceEditCtrl', [
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
		$scope.se = null;
		$scope.seVendors = null;
		$scope.seInstanceId = $stateParams.seInstanceId;
		$scope.$evalAsync($http.get(
		"/turing/api/se/vendor").then(
		function (response) {
			$scope.seVendors = response.data;
		}));
		$scope.$evalAsync($http.get(
			"/turing/api/se/" + $scope.seInstanceId).then(
			function (response) {
				$scope.se = response.data;
			}));
	}
]);
turingApp.controller('TurSESNCtrl', [
	"$scope",
	"$http",
	"$window",
	"$state",
	"$rootScope",
	"$translate",
	function ($scope, $http, $window, $state, $rootScope, $translate) {
		$scope.seSNs = null;
		$rootScope.$state = $state;
		$scope.$evalAsync($http.get(
			"/turing/api/se/sn").then(
			function (response) {
				$scope.seSNs = response.data;
			}));
	}]);

