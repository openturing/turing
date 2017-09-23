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
