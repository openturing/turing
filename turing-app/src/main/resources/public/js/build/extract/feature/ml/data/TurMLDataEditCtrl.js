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
