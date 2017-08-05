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

turingApp.config(function ($stateProvider, $urlRouterProvider,$locationProvider, $translateProvider) {
	$translateProvider.useSanitizeValueStrategy('escaped');
	$translateProvider
			.translations(
					'en',
					{
						
						NLP_EDIT: "Edit NLP",
						NLP_EDIT_SUBTITLE: "Change the NLP Settings",
						NAME: "Name",
						DESCRIPTION: "Description",
						NLP_VENDORS : "Vendors",
						HOST : "Host",
						PORT : "Port",
						SETTINGS_SAVE_CHANGES : "Save Changes"						
					});
	$translateProvider
			.translations(
					'pt',
					{
						NLP_EDIT: "Editar o NLP",
						NLP_EDIT_SUBTITLE: "Altere as configurações do NLP",
						NAME: "Nome",
						DESCRIPTION: "Descrição",		
						NLP_VENDORS : "Produtos",
						HOST : "Host",
						PORT : "Porta",
						SETTINGS_SAVE_CHANGES : "Salvar Alterações"
					});
	$translateProvider.fallbackLanguage('en');
	
	$urlRouterProvider.otherwise('/home');
	$stateProvider
		.state('home', {
			url: '/home',
			templateUrl: 'templates/home.html',
			controller: 'TurHomeCtrl',
			data : { pageTitle: 'Home | Viglet Turing' }
		})
		.state('ml', {
			url: '/ml',
			templateUrl: 'templates/ml/ml.html',			
			data : { pageTitle: 'Machine Learning | Viglet Turing' }
		})
		.state('ml.instance', {
			url: '/instance',
			templateUrl: 'templates/ml/ml-instance.html',	
			controller: 'TurMLInstanceCtrl',
			data : { pageTitle: 'Machine Learnings | Viglet Turing' }
		})
		.state('ml.model', {
			url: '/model',
			templateUrl: 'templates/ml/model/ml-model.html',	
			controller: 'TurMLModelCtrl',
			data : { pageTitle: 'Machine Learning Models | Viglet Turing' }
		})
		.state('ml.datagroup', {
			url: '/datagroup',
			templateUrl: 'templates/ml/data/group/ml-datagroup.html',	
			controller: 'TurMLDataGroupCtrl',
			data : { pageTitle: 'Machine Learning Data Groups | Viglet Turing' }
		})
		.state('se', {
			url: '/se',
			templateUrl: 'templates/se/se.html',			
			data : { pageTitle: 'Search Engine | Viglet Turing' }
		})
		.state('se.instance', {
			url: '/instance',
			templateUrl: 'templates/se/se-instance.html',	
			controller: 'TurSEInstanceCtrl',
			data : { pageTitle: 'Search Engines | Viglet Turing' }
		})
		.state('se.sn', {
			url: '/sn',
			templateUrl: 'templates/se/sn/se-sn.html',	
			controller: 'TurSESNCtrl',
			data : { pageTitle: 'Semantic Navigation | Viglet Turing' }
		})
		.state('nlp', {
			url: '/nlp',
			templateUrl: 'templates/nlp/nlp.html',			
			data : { pageTitle: 'NLP | Viglet Turing' }
		})
		.state('nlp.instance', {
			url: '/instance',
			templateUrl: 'templates/nlp/nlp-instance.html',
			controller: 'TurNLPInstanceCtrl',
			data : { pageTitle: 'NLPs | Viglet Turing' }
		})
		.state('nlp.instance-edit', {
			url: '/instance/:nlpInstanceId',
			templateUrl: 'templates/nlp/nlp-instance-edit.html',
			controller: 'TurNLPInstanceEditCtrl',
			data : { pageTitle: 'Edit NLP | Viglet Turing' }
		})
		.state('nlp.validation', {
			url: '/validation',
			templateUrl: 'templates/nlp/nlp-validation.html',
			controller: 'TurNLPValidationCtrl',
			data : { pageTitle: 'NLP Validation | Viglet Turing' }
		})
		.state('nlp.entity', {
			url: '/entity',
			templateUrl: 'templates/nlp/entity/nlp-entity.html',
			controller: 'TurNLPEntityCtrl',
			data : { pageTitle: 'NLP Entities | Viglet Turing' }
		})
		.state('nlp.entity-import', {
			url: '/entity/import',
			templateUrl: 'templates/nlp/entity/nlp-entity-import.html',			
			data : { pageTitle: 'Import Entity | Viglet Turing' }
		})
		.state('nlp.entity-edit', {
			url: '/entity/:nlpEntityId',
			templateUrl: 'templates/nlp/entity/nlp-entity-edit.html',
			controller: 'TurNLPEntityEditCtrl',
			data : { pageTitle: 'Edit Entity | Viglet Turing' }
		})
		.state('nlp.entity-edit.term', {
			url: '/term',
			templateUrl: 'templates/nlp/entity/nlp-entity-term.html',			
			data : { pageTitle: 'Entity Terms | Viglet Turing' }
		})
		.state('organization.user', {
			url: '/user',
			templateUrl: 'user.html',
			controller: 'TurUserCtrl',
			data : { pageTitle: 'Users | Viglet Turing' }
		})
		.state('organization.user-new', {
			url: '/user/new',
			templateUrl: 'user-item.html',
			controller: 'TurUserNewCtrl',
			data : { pageTitle: 'New User | Viglet Turing' }
		})
		.state('organization.user-edit', {
			url: '/user/:userId',
			templateUrl: 'user-item.html',
			controller: 'TurUserEditCtrl',
			data : { pageTitle: 'Edit User | Viglet Turing' }
		})
		.state('organization.role', {
			url: '/role',
			templateUrl: 'role.html',
			controller: 'TurRoleCtrl',
			data : { pageTitle: 'Roles | Viglet Turing' }
		})
		.state('organization.role-new', {
			url: '/role/new',
			templateUrl: 'role-item.html',
			controller: 'TurRoleNewCtrl',
			data : { pageTitle: 'New Role | Viglet Turing' }
		})
		.state('organization.role-edit', {
			url: '/role/:roleId',
			templateUrl: 'role-item.html',
			controller: 'TurRoleEditCtrl',
			data : { pageTitle: 'Edit Role | Viglet Turing' }
		})
		.state('organization.group', {
			url: '/group',
			templateUrl: 'group.html',
			controller: 'TurGroupCtrl',
			data : { pageTitle: 'Groups | Viglet Turing' }
		})
		.state('organization.group-new', {
			url: '/group/new',
			templateUrl: 'group-item.html',
			controller: 'TurGroupNewCtrl',
			data : { pageTitle: 'New Group | Viglet Turing' }
		})
		.state('organization.group-edit', {
			url: '/group/:groupId',
			templateUrl: 'group-item.html',
			controller: 'TurGroupEditCtrl',
			data : { pageTitle: 'Edit Group | Viglet Turing' }
		})
		.state('app', {
			url: '/app',
			templateUrl: 'app.html',
			controller: 'TurAppCtrl',
			data : { pageTitle: 'Apps | Viglet Turing'}
		})
		.state('app-new', {
			url: '/app/new',
			templateUrl: 'app-item.html',
			controller: 'TurAppNewCtrl',
			data : { pageTitle: 'New App | Viglet Turing', saveButton: 'Save'}
		})
		.state('app-edit', {
			url: '/app/:appId',
			templateUrl: 'app-item.html',
			controller: 'TurAppEditCtrl',
			data : { pageTitle: 'Edit App | Viglet Turing', saveButton: 'Update Settings' }
		})	
		.state('app-edit.keys', {
			url: '/keys',
			templateUrl: 'app-item-keys.html',
			controller: 'TurAppEditCtrl',
			data : { pageTitle: 'Edit App Keys | Viglet Turing' }
		});

});

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
			
			postData = 'vigText=' + $scope.text + "&vigNLP=" + $scope.nlpmodel
					+ "&vigSE=" + 0;
			$http({
				method : 'POST',
				url : '/turing/api/se/update',
				data : postData, // forms user object
				headers : {
					'Content-Type' : 'application/x-www-form-urlencoded'
				}
			}).success(function(data, status, headers, config) {
				$scope.results = data;

			});

		};
	}]);

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

turingApp.controller('TurMLModelCtrl', [
	"$scope",
	"$http",
	"$window",
	"$state",
	"$rootScope",
	"$translate",
	function ($scope, $http, $window, $state, $rootScope, $translate) {
		$scope.mlModels = null;
		$rootScope.$state = $state;
		$scope.$evalAsync($http.get(
			"/turing/api/ml/model").then(
			function (response) {
				$scope.mlModels = response.data;
			}));
	}]);

turingApp.controller('TurMLDataGroupCtrl', [
	"$scope",
	"$http",
	"$window",
	"$state",
	"$rootScope",
	"$translate",
	function ($scope, $http, $window, $state, $rootScope, $translate) {
		$scope.mlDataGroups = null;
		$rootScope.$state = $state;
		$scope.$evalAsync($http.get(
			"/turing/api/ml/data/group").then(
			function (response) {
				$scope.mlDataGroups = response.data;
			}));
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
				$scope.nlp = response.data.nlp;
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
				$scope.entity = response.data.entity;
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

turingApp.controller('TurMappingNewCtrl', [
	"$scope",
	"$http",
	"$window",
	"$state",
	"$rootScope",
	"$translate",
	function ($scope, $http, $window, $state, $rootScope, $translate) {
		$rootScope.$state = $state;
		$scope.mapping = {};
		$scope.mappingSave = function () {
			var parameter = JSON.stringify($scope.mapping);
			$http.post("../api/mapping/",
				parameter).then(
				function (data, status, headers, config) {
					$state.go('mapping');
				}, function (data, status, headers, config) {
					$state.go('mapping');

				});
		}
	}
]);



turingApp.controller('TurUserCtrl', [
	"$scope",
	"$http",
	"$window",
	"$state",
	"$rootScope",
	"$translate",
	function ($scope, $http, $window, $state, $rootScope, $translate) {
		$rootScope.$state = $state;
		$scope.users = null;

		$scope.$evalAsync($http.get(
			"../api/user/").then(
			function (response) {
				$scope.users = response.data;
			}));

		$scope.userDelete = function (userId) {
			$http.delete("../api/user/" + userId).then(
				function (data, status, headers, config) {
					$http.get(
						"../api/user/").then(
						function (response) {
							$scope.users = response.data;
						});
				}, function (data, status, headers, config) {
					// called asynchronously if an error occurs
					// or server returns response with an error
					// status.
				});
		}
	}]);

turingApp.controller('TurUserNewCtrl', [
	"$scope",
	"$http",
	"$window",
	"$state",
	"$rootScope",
	"$translate",
	function ($scope, $http, $window, $state, $rootScope, $translate) {
		$rootScope.$state = $state;
		$scope.user = {};
		$scope.userSave = function () {
			var parameter = JSON.stringify($scope.user);
			$http.post("../api/user/",
				parameter).then(
				function (data, status, headers, config) {					
			          $state.go('organization.user');
				}, function (data, status, headers, config) {
			          $state.go('organization.user');
				});
		}
	}
]);

turingApp.controller('TurUserEditCtrl', [
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
		$scope.userId = $stateParams.userId;
		$scope.$evalAsync($http.get(
			"../api/user/" + $scope.userId).then(
			function (response) {
				$scope.user = response.data;
			}));
		$scope.userSave = function () {
			$scope.users = null;
			var parameter = JSON.stringify($scope.user);
			$http.put("../api/user/" + $scope.userId,
				parameter).then(
				function (data, status, headers, config) {
					  $state.go('organization.user');
				}, function (data, status, headers, config) {
					  $state.go('organization.user');
				});
		}
	}
]);

turingApp.controller('TurRoleCtrl', [
	"$scope",
	"$http",
	"$window",
	"$state",
	"$rootScope",
	"$translate",
	function ($scope, $http, $window, $state, $rootScope, $translate) {
		$rootScope.$state = $state;
		$scope.roles = null;

		$scope.$evalAsync($http.get(
			"../api/role/").then(
			function (response) {
				$scope.roles = response.data;
			}));

		$scope.roleDelete = function (roleId) {
			$http.delete("../api/role/" + roleId).then(
				function (data, status, headers, config) {
					$http.get(
						"../api/role/").then(
						function (response) {
							$scope.roles = response.data;
						});
				}, function (data, status, headers, config) {
					// called asynchronously if an error occurs
					// or server returns response with an error
					// status.
				});
		}
	}]);

turingApp.controller('TurRoleNewCtrl', [
	"$scope",
	"$http",
	"$window",
	"$state",
	"$rootScope",
	"$translate",
	function ($scope, $http, $window, $state, $rootScope, $translate) {
		$rootScope.$state = $state;
		$scope.role = {};
		$scope.roleSave = function () {
			var parameter = JSON.stringify($scope.role);
			$http.post("../api/role/",
				parameter).then(
				function (data, status, headers, config) {
					  $state.go('organization.role');
				}, function (data, status, headers, config) {
					  $state.go('organization.role');
				});
		}
	}
]);

turingApp.controller('TurRoleEditCtrl', [
	"$scope",
	"$http",
	"$window",
	"$stateParams",
	"$state",
	"$rootScope",
	"$translate",
	function ($scope, $http, $window, $stateParams, $state, $rootScope, $translate) {
		$rootScope.$state = $state;
		$scope.roleId = $stateParams.roleId;
		$scope.$evalAsync($http.get(
			"../api/role/" + $scope.roleId).then(
			function (response) {
				$scope.role = response.data;
			}));
		$scope.roleSave = function () {
			$scope.roles = null;
			var parameter = JSON.stringify($scope.role);
			$http.put("../api/role/" + $scope.roleId,
				parameter).then(
				function (data, status, headers, config) {
					  $state.go('organization.role');
				}, function (data, status, headers, config) {
					  $state.go('organization.role');
				});
		}
	}
]);

turingApp.controller('TurGroupCtrl', [
	"$scope",
	"$http",
	"$window",
	"$state",
	"$rootScope",
	"$translate",
	function ($scope, $http, $window, $state, $rootScope, $translate) {
		$rootScope.$state = $state;
		$scope.groups = null;

		$scope.$evalAsync($http.get(
			"../api/group/").then(
			function (response) {
				$scope.groups = response.data;
			}));

		$scope.groupDelete = function (groupId) {
			$http.delete("../api/group/" + groupId).then(
				function (data, status, headers, config) {
					$http.get(
						"../api/group/").then(
						function (response) {
							$scope.groups = response.data;
						});
				}, function (data, status, headers, config) {
					// called asynchronously if an error occurs
					// or server returns response with an error
					// status.
				});
		}
	}]);

turingApp.controller('TurGroupNewCtrl', [
	"$scope",
	"$http",
	"$window",
	"$state",
	"$rootScope",
	"$translate",
	function ($scope, $http, $window, $state, $rootScope, $translate) {
		$rootScope.$state = $state;
		$scope.group = {};
		$scope.groupSave = function () {
			var parameter = JSON.stringify($scope.group);
			$http.post("../api/group/",
				parameter).then(
				function (data, status, headers, config) {
					$state.go('organization.group');
				}, function (data, status, headers, config) {
					$state.go('organization.group');
				});
		}
	}
]);

turingApp.controller('TurGroupEditCtrl', [
	"$scope",
	"$http",
	"$window",
	"$stateParams",
	"$state",
	"$rootScope",
	"$translate",
	function ($scope, $http, $window, $stateParams, $state, $rootScope, $translate) {
		$rootScope.$state = $state;
		$scope.groupId = $stateParams.groupId;
		$scope.$evalAsync($http.get(
			"../api/group/" + $scope.groupId).then(
			function (response) {
				$scope.group = response.data;
			}));
		$scope.groupSave = function () {
			$scope.groups = null;
			var parameter = JSON.stringify($scope.group);
			$http.put("../api/group/" + $scope.groupId,
				parameter).then(
				function (data, status, headers, config) {
					$state.go('organization.group');
				}, function (data, status, headers, config) {
					$state.go('organization.group');
				});
		}
	}
]);

turingApp.controller('TurAppCtrl', [
	"$scope",
	"$http",
	"$window",
	"$state",
	"$rootScope",
	"$translate",
	function ($scope, $http, $window, $state, $rootScope, $translate) {
		$rootScope.$state = $state;
		$scope.apps = null;

		$scope.$evalAsync($http.get(
			"../api/app/").then(
			function (response) {
				$scope.apps = response.data;
			}));

		$scope.appDelete = function (appId) {
			$http.delete("../api/app/" + appId).then(
				function (data, status, headers, config) {
					$http.get(
						"../api/app/").then(
						function (response) {
							$scope.apps = response.data;
						});
				}, function (data, status, headers, config) {
					// called asynchronously if an error occurs
					// or server returns response with an error
					// status.
				});
		}
	}]);

turingApp.controller('TurAppNewCtrl', [
	"$scope",
	"$http",
	"$window",
	"$state",
	"$rootScope",
	"$translate",
	function ($scope, $http, $window, $state, $rootScope, $translate) {
		$rootScope.$state = $state;
		$scope.app = {};
		$scope.appSave = function () {
			var parameter = JSON.stringify($scope.app);
			$http.post("../api/app/",
				parameter).then(
				function (data, status, headers, config) {
					$state.go('app');
				}, function (data, status, headers, config) {
					$state.go('app');
				});
		}
	}
]);

turingApp.controller('TurAppEditCtrl', [
	"$scope",
	"$http",
	"$window",
	"$stateParams",
	"$state",
	"$rootScope",
	"$translate",
	function ($scope, $http, $window, $stateParams, $state, $rootScope, $translate) {
		$rootScope.$state = $state;
		$scope.appId = $stateParams.appId;
		$scope.$evalAsync($http.get(
			"../api/app/" + $scope.appId).then(
			function (response) {
				$scope.app = response.data;
			}));
		$scope.appSave = function () {
			$scope.apps = null;
			var parameter = JSON.stringify($scope.app);
			$http.put("../api/app/" + $scope.appId,
				parameter).then(
				function (data, status, headers, config) {
					$state.go('app');
				}, function (data, status, headers, config) {
					$state.go('app');
				});
		}
	}
]);