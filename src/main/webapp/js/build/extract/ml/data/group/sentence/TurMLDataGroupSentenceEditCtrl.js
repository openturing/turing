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
					$scope.sentence.$delete(function() {
						turNotificationService
								.addNotification($scope.deletedMessage);
						$state.go('ml.datagroup');
					});
				}, function() {
					// Selected NO
				});

			}

		} ]);
