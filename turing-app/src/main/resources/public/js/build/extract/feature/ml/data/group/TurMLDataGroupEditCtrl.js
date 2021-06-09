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
