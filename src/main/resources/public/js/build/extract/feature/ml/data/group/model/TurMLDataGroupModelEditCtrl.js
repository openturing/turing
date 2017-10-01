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
