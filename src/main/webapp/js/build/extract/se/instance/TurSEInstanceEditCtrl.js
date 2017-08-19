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
		"$uibModal",
		function($scope, $stateParams, $state, $rootScope, $translate,
				vigLocale, turSEInstanceResource, turSEVendorResource, turLocaleResource, $uibModal) {

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
					$state.go('se.instance');
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
					$scope.se.$delete(function() {
						$state.go('se.instance');
					});
				}, function() {
					// Selected NO
				});

			}

		} ]);
