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
		"$uibModal",
		function($scope, $stateParams, $state, $rootScope, $translate,
				vigLocale, turSNSiteResource, turSEInstanceResource,
				turNLPInstanceResource, $uibModal) {

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
					$state.go('sn.site');
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
