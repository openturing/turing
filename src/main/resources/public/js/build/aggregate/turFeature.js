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
turingApp.controller('TurSNSiteHLCtrl', [
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
turingApp.controller('TurSNSiteFieldCtrl', [
		"$scope",
		"$http",
		"$window",
		"$state",
		"$rootScope",
		"$translate",
		"$uibModal",
		"$stateParams",
		function($scope, $http, $window, $state, $rootScope, $translate, $uibModal, $stateParams) {
			$rootScope.$state = $state;
			
			$scope.fieldNew = function() {
				var $ctrl = this;
				$scope.snSiteField = {};
				var modalInstance = $uibModal.open({
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
							return  $stateParams.snSiteId;
						}
					}
				});
				
				modalInstance.result.then(function(response) {
					/*delete response.turDataGroupCategories;
					delete response.turDataSentences;
					turMLDataGroupCategory = {};
					turMLDataGroupCategory.turMLCategory = response;
					turMLDataGroupCategoryResource.save({
						dataGroupId : $stateParams.mlDataGroupId
					}, turMLDataGroupCategory);*/

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
turingApp.factory('turSNSiteFieldResource', [ '$resource', 'turAPIServerService', function($resource, turAPIServerService) {
	return $resource(turAPIServerService.get().concat('/sn/:snSiteId/field/:id'), {
		id : '@id',
		snSiteId : '@snSiteId'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);
turingApp.controller('TurSNSiteFacetCtrl', [
		"$scope",
		"$http",
		"$window",
		"$state",
		"$rootScope",
		"$translate",
		function($scope, $http, $window, $state, $rootScope, $translate) {
			$rootScope.$state = $state;
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
