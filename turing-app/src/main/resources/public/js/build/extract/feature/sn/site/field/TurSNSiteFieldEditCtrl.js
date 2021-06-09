turingApp.controller('TurSNSiteFieldEditCtrl', [
	"$scope",
	"$stateParams",
	"$state",
	"$rootScope",
	"$translate",
	"vigLocale",
	"turSNSiteFieldExtResource",
	"turNotificationService",
	"$uibModal",
		function($scope, $stateParams, $state, $rootScope, $translate,
				vigLocale, turSNSiteFieldExtResource, turNotificationService, $uibModal) {
			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);
			$rootScope.$state = $state;

			$scope.snSiteFieldExt = turSNSiteFieldExtResource.get({
				id : $stateParams.snSiteFieldId,
				snSiteId : $stateParams.snSiteId
			});
			$scope.snSiteFieldExtUpdate = function() {			
				$scope.snSiteFieldExt.$update({				
					snSiteId : $stateParams.snSiteId
				}, function() {
					turNotificationService.addNotification("Field \"" + $scope.snSiteFieldExt.name + "\" was saved.");
				});
			}

			$scope.snSiteFieldExtDelete = function() {
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
							return $scope.snSiteFieldExt.name;
						}
					}
				});

				modalInstance.result.then(function(removeInstance) {
					$scope.removeInstance = removeInstance;
					$scope.deletedMessage = "Field \"" + $scope.snSiteFieldExt.name  + "\" was deleted.";
					$scope.snSiteFieldExt.$delete({				
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