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
