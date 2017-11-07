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