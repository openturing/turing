var turingApp = angular.module('turingApp', [  'ngCookies','ngResource', 'ngAnimate',
		'ngSanitize', 'ui.router', 'ui.bootstrap', 'pascalprecht.translate' ]);
turingApp.controller('TurAlertCtrl', [ "$scope", "turNotificationService",
		function($scope, turNotificationService) {
		$scope.alerts = turNotificationService.notifications;

			$scope.closeAlert = function(index) {
				turNotificationService.notifications.splice(index, 1);
			};
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
				SETTINGS_SAVE_CHANGES : "Save Changes",
				INTERNAL_NAME :  "Internal Name",
				SENTENCE: "Sentence",
				SENTENCES: "Sentences",
				CATEGORY: "Category",
				LANGUAGE: "Language",
				CORE: "Core",
				SE: "Search Engine",
				NLP: "NLP",
				TYPE: "Type",
				FACET: "Facet",
				FACETS: "Facets",
				FACET_NAME: "Facet Name",
				MULTI_VALUED: "Multi Valued",
				HIGHLIGHTING: "Highlighting",
				NEW_FIELD: "New Field",
				FIELD: "Field",
				FIELDS: "Fields",
				DETAIL: "Detail",
				APPEARANCE: "Appearance"	,
				DELETE: "Delete",
				NEW: "New",
				ENABLED: "Enabled",
				SYNC: "Sync"
			
					
			});
			$translateProvider.translations('pt', {
				NLP_EDIT : "Editar o NLP",
				NLP_EDIT_SUBTITLE : "Altere as configurações do NLP",
				NAME : "Nome",
				DESCRIPTION : "Descrição",
				VENDORS : "Produtos",
				HOST : "Host",
				PORT : "Porta",
				SETTINGS_SAVE_CHANGES : "Salvar Alterações",
				INTERNAL_NAME :  "Nome Interno",
				SENTENCE: "Sentença",
				SENTENCES: "Sentenças",
				CATEGORY: "Categoria",
				LANGUAGE: "Idioma",
				CORE: "Instância",
				SE: "Motor de Busca",
				NLP: "NLP",
				TYPE: "Tipo",
				FACET: "Faceta",
				FACETS: "Facetas",
				FACET_NAME: "Nome da Faceta",
				MULTI_VALUED: "Múltiplos Valores",
				HIGHLIGHTING: "Realce",
				NEW_FIELD: "Novo Campo",
				FIELD: "Campo",
				FIELDS: "Campos",
				DETAIL: "Detalhe",
				APPEARANCE: "Aparência",
				DELETE: "Apagar",
				NEW: "Novo",
				ENABLED: "Ativado",
				SYNC: "Sincronizar"


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
				controller : 'TurMLDataGroupCategoryCtrl',
				data : {
					pageTitle : 'Data Group Categories | Viglet Turing'
				}
			}).state('ml.datagroup-edit.category-edit', {
				url : '/category/:mlCategoryId',
				templateUrl : 'templates/ml/category/ml-category-edit.html',
				controller : 'TurMLCategoryEditCtrl',
				data : {
					pageTitle : 'Edit Category | Viglet Turing'
				}
			}).state('ml.datagroup-edit.category-edit.sentence', {
				url : '/sentence',
				templateUrl : 'templates/ml/category/ml-category-sentence.html',
				controller : 'TurMLCategorySentenceCtrl',
				data : {
					pageTitle : 'Edit Category | Viglet Turing'
				}
			}).state('ml.datagroup-edit.data', {
				url : '/data',
				templateUrl : 'templates/ml/data/group/ml-datagroup-data.html',
				controller : 'TurMLDataGroupDataCtrl',
				data : {
					pageTitle : 'Data Group Documents | Viglet Turing'
				}
			}).state('ml.datagroup-edit.data-edit', {
				url : '/data/:mlDataId',
				templateUrl : 'templates/ml/data/ml-data-edit.html',
				controller : 'TurMLDataEditCtrl',
				data : {
					pageTitle : 'Edit Data | Viglet Turing'
				}
			}).state('ml.datagroup-edit.sentence', {
				url : '/sentence',
				templateUrl : 'templates/ml/data/group/ml-datagroup-sentence.html',
				controller : 'TurMLDataGroupSentenceCtrl',
				data : {
					pageTitle : 'Data Group Sentences | Viglet Turing'
				}
			}).state('ml.datagroup-edit.sentence-edit', {
				url : '/sentence/:mlSentenceId',
				templateUrl : 'templates/ml/sentence/ml-sentence-edit.html',
				controller : 'TurMLDataGroupSentenceEditCtrl',
				data : {
					pageTitle : 'Edit Sentence | Viglet Turing'
				}
			}).state('ml.datagroup-edit.model', {
				url : '/model',
				templateUrl : 'templates/ml/data/group/ml-datagroup-model.html',
				controller : 'TurMLDataGroupModelCtrl',
				data : {
					pageTitle : 'Data Group Models | Viglet Turing'
				}
			}).state('ml.datagroup-edit.model-edit', {
				url : '/model/:mlModelId',
				templateUrl : 'templates/ml/model/ml-model-edit.html',
				controller : 'TurMLDataGroupModelEditCtrl',
				data : {
					pageTitle : 'Edit Model | Viglet Turing'
				}
			}).state('ml.datagroup-edit.data-edit.sentence', {
				url : '/sentence',
				templateUrl : 'templates/ml/data/ml-data-sentence.html',
				controller : 'TurMLDataSentenceCtrl',
				data : {
					pageTitle : 'Edit Data | Viglet Turing'
				}
			}).state('converse', {
				url : '/converse',
				templateUrl : 'templates/converse/converse.html',
				data : {
					pageTitle : 'Converse | Viglet Turing'
				}
			}).state('converse.intent', {
				url : '/intent',
				templateUrl : 'templates/converse/converse-intent.html',
				controller : 'TurConverseIntentCtrl',
				data : {
					pageTitle : 'Converse Intents | Viglet Turing'
				}
			}).state('converse.entity', {
				url : '/intent',
				templateUrl : 'templates/converse/converse-entity.html',
				controller : 'TurConverseEntityCtrl',
				data : {
					pageTitle : 'Converse Entity | Viglet Turing'
				}
			}).state('converse.training', {
				url : '/training',
				templateUrl : 'templates/converse/converse-training.html',
				controller : 'TurConverseTrainingCtrl',
				data : {
					pageTitle : 'Converse Training | Viglet Turing'
				}
			}).state('converse.prebuilt-agent', {
				url : '/prebuiltagent',
				templateUrl : 'templates/converse/converse-prebuilt-agent.html',
				controller : 'TurConversePreBuiltAgentCtrl',
				data : {
					pageTitle : 'Converse Prebuilt Agents | Viglet Turing'
				}
			}).state('storage', {
				url : '/storage',
				templateUrl : 'templates/storage/storage.html',
				data : {
					pageTitle : 'Storage | Viglet Turing'
				}
			}).state('storage.instance', {
				url : '/instance',
				templateUrl : 'templates/storage/storage-instance.html',
				controller : 'TurStorageInstanceCtrl',
				data : {
					pageTitle : 'Storages | Viglet Turing'
				}
			}).state('storage.mgmt', {
				url : '/mgmt',
				templateUrl : 'templates/storage/mgmt/storage-mgmt.html',
				controller : 'TurStorageMgmtCtrl',
				data : {
					pageTitle : 'Storages | Viglet Turing'
				}
			}).state('storage.mgmt-child', {				
				url: '/mgmt?path',
				templateUrl : 'templates/storage/mgmt/storage-mgmt-child.html',
				controller : 'TurStorageMgmtCtrl',
				data : {
					pageTitle : 'Storages | Viglet Turing'
				}
			})
			.state('se', {
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
				templateUrl : 'templates/sn/site/sn-site.html',
				controller : 'TurSNSiteCtrl',
				data : {
					pageTitle : 'Semantic Navigation Sites | Viglet Turing'
				}
			}).state('sn.site-new', {
				url : '/site/new',
				templateUrl : 'templates/sn/site/sn-site-new.html',
				controller : 'TurSNSiteNewCtrl',
				data : {
					pageTitle : 'New Semantic Navigation Site | Viglet Turing'
				}
			}).state('sn.site-edit', {
				url : '/site/:snSiteId',
				templateUrl : 'templates/sn/site/sn-site-edit.html',
				controller : 'TurSNSiteEditCtrl',
				data : {
					pageTitle : 'Edit Semantic Navigation Site | Viglet Turing'
				}
			}).state('sn.site-edit.field', {
				url : '/field',
				templateUrl : 'templates/sn/site/sn-site-field.html',
				controller : 'TurSNSiteFieldCtrl',
				data : {
					pageTitle : 'Semantic Navigation Site Fields | Viglet Turing'
				}
			}).state('sn.site-edit.field-edit', {
				url : '/field/:snSiteFieldId',
				templateUrl : 'templates/sn/site/field/sn-site-field-edit.html',
				controller : 'TurSNSiteFieldEditCtrl',
				data : {
					pageTitle : 'Edit Semantic Navigation Site Field | Viglet Turing'
				}
			}).state('sn.site-edit.ui', {
				url : '/ui',
				templateUrl : 'templates/sn/site/sn-site-ui.html',
				controller : 'TurSNSiteUICtrl',
				data : {
					pageTitle : 'Semantic Navigation Site Appearance | Viglet Turing'
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
turingApp.directive('fileModel', [ '$parse', function($parse) {
	return {
		restrict : 'A',
		link : function(scope, element, attrs) {
			var model = $parse(attrs.fileModel);
			var modelSetter = model.assign;

			element.bind('change', function() {
				scope.$apply(function() {
					modelSetter(scope, element[0].files[0]);
				});
			});
		}
	};
} ]);
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
turingApp.service('turNotificationService', [ '$http', function($http) {
	this.notifications = [];
	this.addNotification = function(msgString) {
		this.notifications.push({
			msg : msgString
		});
	};

} ]);
turingApp.service('fileUpload', [ '$http', function($http) {

	this.uploadFileToUrl = function(file, uploadUrl) {
		var fd = new FormData();
		fd.append('file', file);
		return $http.post(uploadUrl, fd, {
			transformRequest : angular.identity,
			headers : {
				'Content-Type' : undefined
			}
		});
	}
} ]);
turingApp.service('turAPIServerService', [
		'$http',
		'$location',
		'$cookies',
		function($http, $location, $cookies) {
			var turProtocol = $location.protocol();
			var turHostname = $location.host();
			var turPort = $location.port();
			var turAPIContext = "/api";
			var turEmbServer = turProtocol + "://" + turHostname + ":"
					+ turPort + turAPIContext;
			console.log(turEmbServer);

			this.get = function() {

				if ($cookies.get('turAPIServer') != null)
					return $cookies.get('turAPIServer');
				else {
					$http({
						method : 'GET',
						url : turEmbServer
					}).then(function successCallback(response) {
						$cookies.put('turAPIServer', turEmbServer);
					}, function errorCallback(response) {
						$cookies.put('turAPIServer', 'http://localhost:2700' + turAPIContext);

					});
					return turEmbServer;
				}
			}
		} ]);
turingApp.factory('turLocaleResource', [ '$resource', 'turAPIServerService', function($resource, turAPIServerService) {
	return $resource(turAPIServerService.get().concat('/locale/:id'), {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);
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
		"turNotificationService",
		"$uibModal",
		function($scope, $stateParams, $state, $rootScope, $translate,
				vigLocale, turSEInstanceResource, turSEVendorResource, turLocaleResource, turNotificationService, $uibModal) {

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
					turNotificationService.addNotification("Search Engine Instance \"" + $scope.se.title + "\" was saved.");
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
					$scope.deletedMessage = "Search Engine Instance \"" + $scope.se.title + "\" was deleted.";
					$scope.se.$delete(function() {
						turNotificationService.addNotification($scope.deletedMessage);
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
		"turNotificationService",
		function($scope, $state, $rootScope, $translate, vigLocale,
				turSEInstanceResource, turSEVendorResource, turLocaleResource, turNotificationService) {

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
					turNotificationService.addNotification( "Search Engine Instance \"" + $scope.se.title + "\" was created.");
					$state.go('se.instance');
				});
			}
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
turingApp.factory('turSEInstanceResource', [ '$resource',
		'turAPIServerService', function($resource, turAPIServerService) {
			return $resource(turAPIServerService.get().concat('/se/:id'), {
				id : '@id'
			}, {
				update : {
					method : 'PUT'
				}
			});
		} ]);
turingApp.factory('turSEVendorResource', [ '$resource', 'turAPIServerService', function($resource, turAPIServerService) {
	return $resource(turAPIServerService.get().concat('/se/vendor/:id'), {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);
turingApp.controller('TurHomeCtrl', [ "$scope", "$http", "$window", "$state",
		"$rootScope", "$translate",'turAPIServerService',
		function($scope, $http, $window, $state, $rootScope, $translate, turAPIServerService) {
			createServerAPICookie = turAPIServerService.get();
			$scope.accesses = null;
			$rootScope.$state = $state;
		} ]);
turingApp.controller('TurStorageMgmtCtrl', [
		"$scope",
		"$http",
		"$window",
		"$state",
		"$rootScope",
		"$translate",
		"turStorageMgmtResource",
		"$stateParams",
		function($scope, $http, $window, $state, $rootScope, $translate,
				turStorageMgmtResource, $stateParams) {
			$rootScope.$state = $state;
			$scope.currPath = $stateParams.path + "\/";
			console.log("Teste1");
			console.log($state.params.path);
			$scope.getFullPath = function (path){
				return  $stateParams.path + "/" + path;
			}
			if ($stateParams.path.length <= 0) {
				$scope.rootPath = true;
				$scope.filesAndDirs = turStorageMgmtResource.query();
			} else {
				$scope.rootPath = false;
				$scope.filesAndDirs = turStorageMgmtResource.get({
					id : $stateParams.path
				});

			}
		} ]);
turingApp.factory('turStorageMgmtResource', [ '$resource',
		'turAPIServerService', function($resource, turAPIServerService) {
			return $resource(turAPIServerService.get().concat('/storage/hadoop/:id'), {
				id : '@id'
			}, {
				update : {
					method : 'PUT'
				}
			});
		} ]);
turingApp.controller('TurStorageInstanceCtrl', [
		"$scope",
		"$http",
		"$window",
		"$state",
		"$rootScope",
		"$translate",
		"turStorageInstanceResource",
		function($scope, $http, $window, $state, $rootScope, $translate,
				turStorageInstanceResource) {
			$rootScope.$state = $state;
			$scope.ses = turStorageInstanceResource.query();
		} ]);
turingApp.factory('turStorageInstanceResource', [ '$resource',
		'turAPIServerService', function($resource, turAPIServerService) {
			return $resource(turAPIServerService.get().concat('/storage/:id'), {
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
turingApp.controller('TurSNSiteUICtrl', [
		"$scope",
		"$http",
		"$window",
		"$state",
		"$rootScope",
		"$translate",		
		function($scope, $http, $window, $state, $rootScope, $translate) {
			$rootScope.$state = $state;
		
		} ]);
turingApp.controller('TurSNSiteFieldEditCtrl', [
	"$scope",
	"$stateParams",
	"$state",
	"$rootScope",
	"$translate",
	"vigLocale",
	"turSNSiteFieldResource",
	"turNotificationService",
	"$uibModal",
		function($scope, $stateParams, $state, $rootScope, $translate,
				vigLocale, turSNSiteFieldResource, turNotificationService, $uibModal) {
			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);
			$rootScope.$state = $state;

			$scope.snSiteField = turSNSiteFieldResource.get({
				id : $stateParams.snSiteFieldId,
				snSiteId : $stateParams.snSiteId
			});
			$scope.snSiteFieldUpdate = function() {			
				$scope.snSiteField.$update({				
					snSiteId : $stateParams.snSiteId
				}, function() {
					turNotificationService.addNotification("Field \"" + $scope.snSiteField.name + "\" was saved.");
				});
			}

			$scope.snSiteFieldDelete = function() {
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
							return $scope.snSiteField.name;
						}
					}
				});

				modalInstance.result.then(function(removeInstance) {
					$scope.removeInstance = removeInstance;
					$scope.deletedMessage = "Field \"" + $scope.snSiteField.name  + "\" was deleted.";
					$scope.snSiteField.$delete({				
						snSiteId : $stateParams.snSiteId
					}, function() {
						turNotificationService.addNotification($scope.deletedMessage);
						$state.go('sn.site');
					});
				}, function() {
					// Selected NO
				});

			}
		} ]);
turingApp
		.controller(
				'TurSNSiteFieldCtrl',
				[
						"$scope",
						"$http",
						"$window",
						"$state",
						"$rootScope",
						"$translate",
						"$uibModal",
						"$stateParams",
						"turSNSiteFieldResource",
						"turSNSiteFieldExtResource",
						"turNLPEntityLocalResource",
						"turNotificationService",
						"$filter",
						function($scope, $http, $window, $state, $rootScope,
								$translate, $uibModal, $stateParams,
								turSNSiteFieldResource,
								turSNSiteFieldExtResource,
								turNLPEntityLocalResource,
								turNotificationService, $filter) {
							$rootScope.$state = $state;

							$scope.turSNSiteFieldExts = turSNSiteFieldExtResource
									.query({
										snSiteId : $stateParams.snSiteId
									});

							$scope.snSiteFieldUpdate = function(snSiteFieldExt) {
								if (snSiteFieldExt != null) {
									turSNSiteFieldExtResource.update({
										id : snSiteFieldExt.id,
										snSiteId : $stateParams.snSiteId
									}, snSiteFieldExt, function() {
										//
									});
								}
							}
							$scope.fieldNew = function() {
								var $ctrl = this;
								$scope.snSiteField = {};
								var modalInstance = $uibModal
										.open({
											animation : true,
											ariaLabelledBy : 'modal-title',
											ariaDescribedBy : 'modal-body',
											templateUrl : 'templates/sn/site/field/sn-site-field-new.html',
											controller : 'TurSNSiteFieldNewCtrl',
											controllerAs : '$ctrl',
											size : null,
											appendTo : undefined,
											resolve : {
												snSiteField : function() {
													return $scope.snSiteField;
												},
												snSiteId : function() {
													return $stateParams.snSiteId;
												}
											}
										});

								modalInstance.result.then(function(response) {
									//
								}, function() {
									// Selected NO
								});

							}
						} ]);
turingApp.controller('TurSNSiteFieldNewCtrl', [
		"$uibModalInstance",
		"snSiteField",
		"snSiteId",
		"turSNSiteFieldResource",
		"turNotificationService",
		function($uibModalInstance, snSiteField, snSiteId,
				turSNSiteFieldResource, turNotificationService) {
			var $ctrl = this;
			$ctrl.removeInstance = false;
			$ctrl.snSiteField = snSiteField;
			console.log($ctrl.snSiteField);
			$ctrl.ok = function() {
				console.log($ctrl.snSiteField);
				turSNSiteFieldResource.save({
					snSiteId : snSiteId
				}, $ctrl.snSiteField, function(response) {
					turNotificationService.addNotification("Field \""
							+ response.name + "\" was created.");
					$uibModalInstance.close(response);
				});

			};

			$ctrl.cancel = function() {
				$uibModalInstance.dismiss('cancel');
			};
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
		"turNotificationService",
		function($scope, $state, $rootScope, $translate, vigLocale,
				turSNSiteResource, turSEInstanceResource,
				turNLPInstanceResource, turNotificationService) {

			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);

			$rootScope.$state = $state;
			$scope.snSite = {};

			$scope.seInstances = turSEInstanceResource.query({}, function() {
				angular.forEach($scope.seInstances, function(value, key) {
					if (value.selected == true) {
						value.title = value.title;
						$scope.snSite.turSEInstance = value;
					}
				})
			});

			$scope.nlpInstances = turNLPInstanceResource.query({}, function() {
				angular.forEach($scope.nlpInstances, function(value, key) {
					if (value.selected == true) {
						value.title = value.title;
						$scope.snSite.turNLPInstance = value;
					}
				})
			});

			$scope.snSiteSave = function() {
				turSNSiteResource.save($scope.snSite, function() {
					turNotificationService.addNotification("Semantic Navigation Site \"" + $scope.snSite.name + "\" was created.");
					$state.go('sn.site');
				});
			}
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
		"turNotificationService",
		"$uibModal",
		function($scope, $stateParams, $state, $rootScope, $translate,
				vigLocale, turSNSiteResource, turSEInstanceResource,
				turNLPInstanceResource, turNotificationService, $uibModal) {

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
					turNotificationService.addNotification("Semantic Navigation Site \"" + $scope.snSite.name + "\" was saved.");
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
							turNotificationService.addNotification("Semantic Navigation Site \"" + $scope.snSite.name + "\" was deleted.");
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
turingApp.factory('turSNSiteResource', [ '$resource', 'turAPIServerService', function($resource, turAPIServerService) {
	return $resource(turAPIServerService.get().concat('/sn/:id'), {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);
turingApp.factory('turSNSiteFieldExtResource', [
		'$resource',
		'turAPIServerService',
		function($resource, turAPIServerService) {
			return $resource(turAPIServerService.get().concat(
					'/sn/:snSiteId/field/ext/:id'), {
				id : '@id',
				snSiteId : '@snSiteId'
			}, {
				update : {
					method : 'PUT'
				}
			});
		} ]);
turingApp.factory('turSNSiteFieldResource', [
		'$resource',
		'turAPIServerService',
		function($resource, turAPIServerService) {
			return $resource(turAPIServerService.get().concat(
					'/sn/:snSiteId/field/:id'), {
				id : '@id',
				snSiteId : '@snSiteId'
			}, {
				update : {
					method : 'PUT'
				}
			});
		} ]);
turingApp.controller('TurMLCategoryNewCtrl', [
		"$uibModalInstance",
		"category",
		"turMLCategoryResource",
		"turNotificationService",
		function($uibModalInstance, category, turMLCategoryResource,
				turNotificationService) {
			var $ctrl = this;
			$ctrl.removeInstance = false;
			$ctrl.category = category;
			$ctrl.ok = function() {
				turMLCategoryResource.save($ctrl.category, function(response) {
					turNotificationService.addNotification("Category \""
							+ response.name + "\" was created.");
					$uibModalInstance.close(response);
				});

			};

			$ctrl.cancel = function() {
				$uibModalInstance.dismiss('cancel');
			};
		} ]);
turingApp.controller('TurMLCategorySentenceCtrl', [
		"$scope",
		"$stateParams",
		"$state",
		"$rootScope",
		"$translate",
		"vigLocale",
		"$uibModal",
		"turMLDataSentenceResource",
		"turNotificationService",
		function($scope, $stateParams, $state, $rootScope, $translate,
				vigLocale, $uibModal, turMLDataSentenceResource,
				turNotificationService) {

			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);
			$rootScope.$state = $state;
			$scope.sentenceNew = function() {
				var $ctrl = this;
				$scope.sentence = {
					dataGroupId : $stateParams.mlDataGroupId,
					turMLCategoryId : $stateParams.mlCategoryId
				};
				$scope.categoryId = {
						dataGroupId : $stateParams.mlDataGroupId
					};
				var modalInstance = $uibModal.open({
					animation : true,
					ariaLabelledBy : 'modal-title',
					ariaDescribedBy : 'modal-body',
					templateUrl : 'templates/ml/sentence/ml-sentence-new.html',
					controller : 'TurMLSentenceNewCtrl',
					controllerAs : '$ctrl',
					size : null,
					appendTo : undefined,
					resolve : {
						sentence : function() {
							return $scope.sentence;
						}
					}
				});

				modalInstance.result.then(function(response) {
					//
				}, function() {
					// Selected NO
				});

			}
		} ]);
turingApp.controller('TurMLCategoryEditCtrl', [
		"$scope",
		"$stateParams",
		"$state",
		"$rootScope",
		"$translate",
		"vigLocale",
		"turMLCategoryResource",
		"turNotificationService",
		"$uibModal",
		function($scope, $stateParams, $state, $rootScope, $translate,
				vigLocale, turMLCategoryResource, turNotificationService, $uibModal) {

			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);
			$rootScope.$state = $state;

			$scope.category = turMLCategoryResource.get({
				id : $stateParams.mlCategoryId
			});
			$scope.mlCategoryUpdate = function() {
				$scope.category.$update(function() {
					turNotificationService.addNotification("Category \"" + $scope.category.name + "\" was saved.");
				});
			}

			$scope.mlCategoryDelete = function() {
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
							return $scope.category.name;
						}
					}
				});

				modalInstance.result.then(function(removeInstance) {
					$scope.removeInstance = removeInstance;
					$scope.deletedMessage = "Category \"" + $scope.category.name  + "\" was deleted.";
					$scope.category.$delete(function() {
						turNotificationService.addNotification($scope.deletedMessage);
						$state.go('ml.datagroup');
					});
				}, function() {
					// Selected NO
				});

			}

		} ]);
turingApp.factory('turMLCategoryResource', [ '$resource', 'turAPIServerService', function($resource, turAPIServerService) {
	return $resource(turAPIServerService.get().concat('/ml/category/:id'), {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);
turingApp.controller('TurMLSentenceNewCtrl', [
		"$uibModalInstance",
		"sentence",
		"turMLDataGroupSentenceResource",
		"turNotificationService",
		function($uibModalInstance, sentence, turMLDataGroupSentenceResource,
				turNotificationService) {
			var $ctrl = this;
			$ctrl.removeInstance = false;
			$ctrl.dataGroupId = sentence.dataGroupId;
			
			console.log(sentence.turMLCategoryId);
			
			$ctrl.sentence = sentence;
			$ctrl.ok = function() {
				delete sentence.dataGroupId;

				turMLDataGroupSentenceResource.save({
					dataGroupId : $ctrl.dataGroupId
				}, $ctrl.sentence, function(response) {
					turNotificationService.addNotification("Sentence \""
							+ response.sentence + "\" was created.");
					$uibModalInstance.close(response);
				});

			};

			$ctrl.cancel = function() {
				$uibModalInstance.dismiss('cancel');
			};
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
turingApp.factory('turMLModelResource', [
		'$resource',
		'turAPIServerService',
		function($resource, turAPIServerService) {
			return $resource(turAPIServerService.get().concat('/ml/model/:id'),
					{
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
turingApp.controller('TurMLInstanceNewCtrl', [
		"$scope",
		"$state",
		"$rootScope",
		"$translate",
		"vigLocale",
		"turMLInstanceResource",
		"turMLVendorResource",
		"turLocaleResource",
		"turNotificationService",
		function($scope, $state, $rootScope, $translate, vigLocale,
				turMLInstanceResource, turMLVendorResource, turLocaleResource, turNotificationService) {

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
					turNotificationService.addNotification("Machine Learning Instance \"" + $scope.ml.title + "\" was created.");
					$state.go('ml.instance');
				});
			}
		} ]);
turingApp.factory('turMLInstanceResource', [ '$resource',
		'turAPIServerService', function($resource, turAPIServerService) {
			return $resource(turAPIServerService.get().concat('/ml/:id'), {
				id : '@id'
			}, {
				update : {
					method : 'PUT'
				}
			});
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
		"turNotificationService",
		"$uibModal",
		function($scope, $stateParams, $state, $rootScope, $translate,
				vigLocale, turMLInstanceResource, turMLVendorResource,
				turLocaleResource, turNotificationService, $uibModal) {

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
					turNotificationService.addNotification("Machine Learning Instance \"" + $scope.ml.title + "\" was saved.");
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
					$scope.deletedMessage = "Machine Learning Instance \"" + $scope.ml.title + "\" was deleted.";
					$scope.ml.$delete(function() {
						turNotificationService.addNotification($scope.deletedMessage);
						$state.go('ml.instance');
					});
				}, function() {
					// Selected NO
				});

			}
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
turingApp.controller('TurMLDataGroupCategoryCtrl', [
		"$scope",
		"$stateParams",
		"$state",
		"$rootScope",
		"$translate",
		"vigLocale",
		"turMLDataGroupCategoryResource",
		"$uibModal",
		function($scope, $stateParams, $state, $rootScope, $translate,
				vigLocale, turMLDataGroupCategoryResource, $uibModal) {

			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);
			$rootScope.$state = $state;

			$scope.mlDataGroupCategories = turMLDataGroupCategoryResource
					.query({
						dataGroupId : $stateParams.mlDataGroupId
					});

			$scope.categoryNew = function() {
				var $ctrl = this;
				$scope.category = {};
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

				modalInstance.result.then(function(response) {
					delete response.turDataGroupCategories;
					delete response.turDataSentences;
					turMLDataGroupCategory = {};
					turMLDataGroupCategory.turMLCategory = response;
					turMLDataGroupCategoryResource.save({
						dataGroupId : $stateParams.mlDataGroupId
					}, turMLDataGroupCategory);

					//
				}, function() {
					// Selected NO
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
		"turNotificationService",
		"$uibModal",
		"$http",
		"turAPIServerService",
		function($scope, $stateParams, $state, $rootScope, $translate,
				vigLocale, turMLDataGroupResource, turNotificationService,
				$uibModal, $http, turAPIServerService) {

			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);
			$rootScope.$state = $state;

			$scope.dataGroup = turMLDataGroupResource.get({
				id : $stateParams.mlDataGroupId
			});
			$scope.dataGroupSave = function() {
				$scope.dataGroup.$update(function() {
					turNotificationService.addNotification("Data Group \""
							+ $scope.dataGroup.name + "\" was saved.");
				});
			}

			$scope.generateModel = function() {
				$http.get(
						turAPIServerService.get().concat(
								"/ml/data/group/" + $stateParams.mlDataGroupId
										+ "/model/generate")).then(
						function(response) {
							turNotificationService.addNotification("\""
									+ $scope.dataGroup.name
									+ "\" model was generated.");
							$scope.results = response.data;
						}, function(response) {
							//
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
					$scope.deletedMessage = "Data Group \""
							+ $scope.dataGroup.name + "\" was deleted.";
					$scope.dataGroup.$delete(function() {
						turNotificationService
								.addNotification($scope.deletedMessage);
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
		"turNotificationService",
		function($scope, $http, $window, $stateParams, $state, $rootScope,
				$translate, vigLocale, turMLDataGroupResource, turNotificationService) {
			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);
			$rootScope.$state = $state;
			$scope.mlDataGroupId = $stateParams.mlDataGroupId;
			$scope.dataGroup = {};
			$scope.dataGroupSave = function() {
				turMLDataGroupResource.save($scope.dataGroup, function() {
					turNotificationService.addNotification("Data Group \"" + $scope.dataGroup.name + "\" was created.");
					$state.go('ml.datagroup');
				});
			}
		} ]);
turingApp.controller('TurMLDataGroupSentenceEditCtrl', [
		"$scope",
		"$stateParams",
		"$state",
		"$rootScope",
		"$translate",
		"vigLocale",
		"turMLDataGroupSentenceResource",
		"turNotificationService",
		"$uibModal",
		function($scope, $stateParams, $state, $rootScope, $translate,
				vigLocale, turMLDataGroupSentenceResource,
				turNotificationService, $uibModal) {

			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);
			$rootScope.$state = $state;

			$scope.sentence = turMLDataGroupSentenceResource.get({
				dataGroupId : $stateParams.mlDataGroupId,
				id : $stateParams.mlSentenceId
			});
			$scope.mlSentenceUpdate = function() {
				$scope.sentence.$update({
					dataGroupId : $stateParams.mlDataGroupId}, function() {
					turNotificationService.addNotification("Sentence \""
							+ $scope.sentence.sentence + "\" was saved.");
				});
			}

			$scope.mlSentenceDelete = function() {
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
							return $scope.sentence.sentence;
						}
					}
				});

				modalInstance.result.then(function(removeInstance) {
					$scope.removeInstance = removeInstance;
					$scope.deletedMessage = "Sentence \""
							+ $scope.sentence.sentence + "\" was deleted.";
					$scope.sentence.$delete({
						dataGroupId : $stateParams.mlDataGroupId}, function() {
						turNotificationService
								.addNotification($scope.deletedMessage);
						$state.go('ml.datagroup');
					});
				}, function() {
					// Selected NO
				});

			}

		} ]);
turingApp.controller('TurMLDataGroupSentenceCtrl', [
		"$scope",
		"$stateParams",
		"$state",
		"$rootScope",
		"$translate",
		"vigLocale",
		"turMLDataGroupSentenceResource",
		"$uibModal",
		"turNotificationService",
		function($scope, $stateParams, $state, $rootScope, $translate,
				vigLocale, turMLDataGroupSentenceResource, $uibModal, turNotificationService) {

			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);
			$rootScope.$state = $state;

			$scope.mlDataGroupSentences = turMLDataGroupSentenceResource
					.query({
						dataGroupId : $stateParams.mlDataGroupId
					});

			$scope.sentenceUpdate = function(turDataGroupSentence) {
				turMLDataGroupSentenceResource.update({
					dataGroupId : $stateParams.mlDataGroupId,
					id : turDataGroupSentence.id
				}, turDataGroupSentence, function() {
					turNotificationService.addNotification("Sentence \""
							+ turDataGroupSentence.sentence.substring(0, 20)
							+ "...\" was saved.");
				});
			}
			
			$scope.sentenceNew = function() {
				var $ctrl = this;
				$scope.sentence = {
					dataGroupId : $stateParams.mlDataGroupId
				};
				var modalInstance = $uibModal.open({
					animation : true,
					ariaLabelledBy : 'modal-title',
					ariaDescribedBy : 'modal-body',
					templateUrl : 'templates/ml/sentence/ml-sentence-new.html',
					controller : 'TurMLSentenceNewCtrl',
					controllerAs : '$ctrl',
					size : null,
					appendTo : undefined,
					resolve : {
						sentence : function() {
							return $scope.sentence;
						}
					}
				});

				modalInstance.result.then(function(response) {					
					//
				}, function() {
					// Selected NO
				});

			}

		} ]);
turingApp.controller('TurMLDataGroupModelEditCtrl', [
		"$scope",
		"$stateParams",
		"$state",
		"$rootScope",
		"$translate",
		"vigLocale",
		"turMLDataGroupModelResource",
		"turNotificationService",
		"$uibModal",
		function($scope, $stateParams, $state, $rootScope, $translate,
				vigLocale, turMLDataGroupModelResource,
				turNotificationService, $uibModal) {

			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);
			$rootScope.$state = $state;

			$scope.model = turMLDataGroupModelResource.get({
				dataGroupId : $stateParams.mlDataGroupId,
				id : $stateParams.mlModelId
			});
			$scope.mlModelUpdate = function() {
				$scope.model.$update({
					dataGroupId : $stateParams.mlDataGroupId}, function() {
					turNotificationService.addNotification("Model \""
							+ $scope.model.model + "\" was saved.");
				});
			}

			$scope.mlModelDelete = function() {
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
							return $scope.model.model;
						}
					}
				});

				modalInstance.result.then(function(removeInstance) {
					$scope.removeInstance = removeInstance;
					$scope.deletedMessage = "Model \""
							+ $scope.model.model + "\" was deleted.";
					$scope.model.$delete(function() {
						turNotificationService
								.addNotification($scope.deletedMessage);
						$state.go('ml.datagroup');
					});
				}, function() {
					// Selected NO
				});

			}

		} ]);
turingApp.controller('TurMLDataGroupModelCtrl', [
		"$scope",
		"$stateParams",
		"$state",
		"$rootScope",
		"$translate",
		"vigLocale",
		"turMLDataGroupModelResource",
		"$uibModal",
		function($scope, $stateParams, $state, $rootScope, $translate,
				vigLocale, turMLDataGroupModelResource, $uibModal) {

			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);
			$rootScope.$state = $state;

			$scope.mlDataGroupModels = turMLDataGroupModelResource
					.query({
						dataGroupId : $stateParams.mlDataGroupId
					});

			$scope.modelNew = function() {
				var $ctrl = this;
				$scope.model = {
					dataGroupId : $stateParams.mlDataGroupId
				};
				var modalInstance = $uibModal.open({
					animation : true,
					ariaLabelledBy : 'modal-title',
					ariaDescribedBy : 'modal-body',
					templateUrl : 'templates/ml/model/ml-model-new.html',
					controller : 'TurMLModelNewCtrl',
					controllerAs : '$ctrl',
					size : null,
					appendTo : undefined,
					resolve : {
						model : function() {
							return $scope.model;
						}
					}
				});

				modalInstance.result.then(function(response) {					
					//
				}, function() {
					// Selected NO
				});

			}

		} ]);
turingApp.controller('TurMLDataGroupDataCtrl', [
		"$scope",
		"$stateParams",
		"$state",
		"$rootScope",
		"$translate",
		"vigLocale",
		"turMLDataGroupDataResource",
		"$uibModal",
		function($scope, $stateParams, $state, $rootScope, $translate,
				vigLocale, turMLDataGroupDataResource, $uibModal) {

			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);
			$rootScope.$state = $state;

			$scope.mlDataGroupDatas = turMLDataGroupDataResource.query({
				dataGroupId : $stateParams.mlDataGroupId
			});

			$scope.uploadDocument = function() {
				var $ctrl = this;
				$scope.data = {};
				$scope.data.datagroupId = $stateParams.mlDataGroupId;
				var modalInstance = $uibModal.open({
					animation : true,
					ariaLabelledBy : 'modal-title',
					ariaDescribedBy : 'modal-body',
					templateUrl : 'templates/ml/data/ml-document-upload.html',
					controller : 'TurMLDataNewCtrl',
					controllerAs : '$ctrl',
					size : null,
					appendTo : undefined,
					resolve : {
						data : function() {
							return $scope.data;
						}
					}
				});

				modalInstance.result.then(function(response) {
					//
				}, function() {
					// Selected NO
				});

			}

		} ]);
turingApp.factory('turMLDataGroupModelResource', [
		'$resource','turAPIServerService',
		function($resource, turAPIServerService) {
			return $resource(
					turAPIServerService.get().concat('/ml/data/group/:dataGroupId/model/:id'), {
						id : '@id',
						dataGroupId : '@dataGroupId'
					}, {
						update : {
							method : 'PUT'
						}
					});
		} ]);
turingApp.factory('turMLDataGroupResource', [ '$resource', 'turAPIServerService', function($resource, turAPIServerService) {
	return $resource(turAPIServerService.get().concat('/ml/data/group/:id'), {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);
turingApp.factory('turMLDataGroupCategoryResource', [
		'$resource','turAPIServerService',
		function($resource, turAPIServerService) {
			return $resource(
					turAPIServerService.get().concat('/ml/data/group/:dataGroupId/category/:id'), {
						id : '@id',
						dataGroupId : '@dataGroupId'
					}, {
						update : {
							method : 'PUT'
						}
					});
		} ]);
turingApp.factory('turMLDataGroupDataResource', [
		'$resource', 'turAPIServerService',
		function($resource, turAPIServerService) {
			return $resource(
					turAPIServerService.get().concat('/ml/data/group/:dataGroupId/data/:id'), {
						id : '@id',
						dataGroupId : '@dataGroupId'
					}, {
						update : {
							method : 'PUT'
						}
					});
		} ]);
turingApp.factory('turMLDataGroupSentenceResource', [
		'$resource', 'turAPIServerService',
		function($resource, turAPIServerService) {
			return $resource(
					turAPIServerService.get().concat('/ml/data/group/:dataGroupId/sentence/:id'), {
						id : '@id',
						dataGroupId : '@dataGroupId'
					}, {
						update : {
							method : 'PUT'
						}
					});
		} ]);
turingApp.controller('TurMLDataNewCtrl', [
		"$uibModalInstance",
		"data",
		'fileUpload',
		'turNotificationService',
		'turAPIServerService',
		function($uibModalInstance, data, fileUpload, turNotificationService,
				turAPIServerService) {
			var $ctrl = this;
			$ctrl.myFile = null;
			$ctrl.removeInstance = false;
			$ctrl.data = data;
			$ctrl.ok = function() {
				var file = $ctrl.myFile;
				var uploadUrl = turAPIServerService.get().concat(
						'/ml/data/group/' + data.datagroupId + '/data/import');
				var response = null;
				fileUpload.uploadFileToUrl(file, uploadUrl).then(
						function(response) {
							turNotificationService
									.addNotification(response.data.turData.name
											+ "\" file was uploaded.");
							$uibModalInstance.close(response);
						});

			};

			$ctrl.cancel = function() {
				$ctrl.removeInstance = false;
				$uibModalInstance.dismiss('cancel');
			};
		} ]);
turingApp.factory('turMLDataSentenceResource', [
		'$resource',
		'turAPIServerService',
		function($resource, turAPIServerService) {
			return $resource(turAPIServerService.get().concat(
					'/ml/data/sentence/:id'), {
				id : '@id'
			}, {
				update : {
					method : 'PUT'
				}
			});
		} ]);
turingApp.controller('TurMLDataEditCtrl', [
		"$scope",
		"$stateParams",
		"$state",
		"$rootScope",
		"$translate",
		"vigLocale",
		"turMLDataResource",
		"turNotificationService",
		"$uibModal",
		function($scope, $stateParams, $state, $rootScope, $translate,
				vigLocale, turMLDataResource, turNotificationService, $uibModal) {

			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);
			$rootScope.$state = $state;

			$scope.data = turMLDataResource.get({
				id : $stateParams.mlDataId
			});
			$scope.dataSave = function() {
				delete $scope.data.turDataGroupSentences;
				$scope.data.$update(function() {
					turNotificationService.addNotification("Data \"" + $scope.data.name + "\" was saved.");
				});
			}

			$scope.dataDelete = function() {
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
							return $scope.data.name;
						}
					}
				});

				modalInstance.result.then(function(removeInstance) {
					$scope.removeInstance = removeInstance;
					$scope.deletedMessage = "Data \"" + $scope.data.name  + "\" was deleted.";
					$scope.data.$delete(function() {
						turNotificationService.addNotification($scope.deletedMessage);
						$state.go('ml.datagroup');
					});
				}, function() {
					// Selected NO
				});

			}

		} ]);
turingApp.factory('turMLDataResource', [ '$resource', 'turAPIServerService', function($resource, turAPIServerService) {
	return $resource(turAPIServerService.get().concat('/ml/data/:id'), {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);
turingApp.controller('TurMLDataSentenceCtrl', [
		"$scope",
		"$stateParams",
		"$state",
		"$rootScope",
		"$translate",
		"vigLocale",
		"$uibModal",
		"turMLDataGroupCategoryResource",
		"turMLDataGroupSentenceResource",
		"turNotificationService",
		function($scope, $stateParams, $state, $rootScope, $translate,
				vigLocale, $uibModal, turMLDataGroupCategoryResource,
				turMLDataGroupSentenceResource, turNotificationService) {

			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);
			$rootScope.$state = $state;
			$scope.sentenceUpdate = function(turDataGroupSentence) {
				turMLDataGroupSentenceResource.update({
					dataGroupId : $stateParams.mlDataGroupId,
					id : turDataGroupSentence.id
				}, turDataGroupSentence, function() {
					turNotificationService.addNotification("Sentence \""
							+ turDataGroupSentence.sentence.substring(0, 20)
							+ "...\" was saved.");
				});
			}
		} ]);
turingApp.factory('turMLVendorResource', [ '$resource', 'turAPIServerService', function($resource, turAPIServerService) {
	return $resource(turAPIServerService.get().concat('/ml/vendor/:id'), {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
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
turingApp.factory('turNLPEntityLocalResource', [ '$resource', 'turAPIServerService', function($resource, turAPIServerService) {
	return $resource(turAPIServerService.get().concat('/entity/local/:id'), {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);
turingApp.factory('turNLPEntityResource', [ '$resource', 'turAPIServerService', function($resource, turAPIServerService) {
	return $resource(turAPIServerService.get().concat('/entity/:id'), {
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
		"turNotificationService",
		"$uibModal",
		function($scope, $stateParams, $state, $rootScope, $translate,
				vigLocale, turNLPInstanceResource, turNLPVendorResource,turLocaleResource, turNotificationService,
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
					turNotificationService.addNotification("NLP Instance \"" + $scope.nlp.title + "\" was saved.");
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
					$scope.deletedMessage = "NLP Instance \"" + $scope.nlp.title + "\" was deleted.";
					$scope.nlp.$delete(function() {
						turNotificationService.addNotification($scope.deletedMessage);
						$state.go('nlp.instance');						
					});
				}, function() {
					// Selected NO
				});
			}

		} ]);
turingApp.factory('turNLPInstanceResource', [ '$resource', 'turAPIServerService', function($resource, turAPIServerService) {
	return $resource(turAPIServerService.get().concat('/nlp/:id'), {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
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
		"turNotificationService",
		function($scope, $state, $rootScope, $translate, vigLocale,
				turNLPInstanceResource, turNLPVendorResource, turLocaleResource, turNotificationService) {

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
					turNotificationService.addNotification("NLP Instance \"" + $scope.nlp.title + "\" was created.");
					$state.go('nlp.instance');
				});
			}
		} ]);
turingApp.controller('TurNLPValidationCtrl', [
		"$scope",
		"$http",
		"$window",
		"$state",
		"$rootScope",
		"$translate",
		"turNLPInstanceResource",
		"turAPIServerService",
		function($scope, $http, $window, $state, $rootScope, $translate,
				turNLPInstanceResource, turAPIServerService) {
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
				$http.post(turAPIServerService.get().concat('/nlp/' + $scope.nlpmodel + '/validate'),
						parameter).then(function(response) {
					$scope.results = response.data;
				}, function(response) {
					//
				});
			};
		} ]);
turingApp.factory('turNLPVendorResource', [
		'$resource',
		'turAPIServerService',
		function($resource, turAPIServerService) {
			return $resource(turAPIServerService.get()
					.concat('/nlp/vendor/:id'), {
				id : '@id'
			}, {
				update : {
					method : 'PUT'
				}
			});
		} ]);
turingApp.controller('TurConverseEntityCtrl', [
		"$scope",
		"$http",
		"$window",
		"$state",
		"$rootScope",
		"$translate",
		function($scope, $http, $window, $state, $rootScope, $translate) {
			$rootScope.$state = $state;
		} ]);
turingApp.controller('TurConverseIntentCtrl', [
		"$scope",
		"$http",
		"$window",
		"$state",
		"$rootScope",
		"$translate",
		function($scope, $http, $window, $state, $rootScope, $translate) {
			$rootScope.$state = $state;
		} ]);
turingApp.controller('TurConverseTrainingCtrl', [
		"$scope",
		"$http",
		"$window",
		"$state",
		"$rootScope",
		"$translate",
		function($scope, $http, $window, $state, $rootScope, $translate) {
			$rootScope.$state = $state;
		} ]);
turingApp.controller('TurConversePreBuiltAgentCtrl', [
		"$scope",
		"$http",
		"$window",
		"$state",
		"$rootScope",
		"$translate",
		function($scope, $http, $window, $state, $rootScope, $translate) {
			$rootScope.$state = $state;
		} ]);
