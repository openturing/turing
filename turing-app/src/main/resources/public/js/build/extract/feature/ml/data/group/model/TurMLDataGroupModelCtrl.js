turingApp.controller('TurMLDataGroupModelCtrl', [
		"$scope",
		"$stateParams",
		"$state",
		"$rootScope",
		"$translate",
		"vigLocale",
		"turMLDataGroupModelResource",
		"$uibModal",
		function($scope, $stateParams, $state, $rootScope, $translate,
				vigLocale, turMLDataGroupModelResource, $uibModal) {

			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);
			$rootScope.$state = $state;

			$scope.mlDataGroupModels = turMLDataGroupModelResource
					.query({
						dataGroupId : $stateParams.mlDataGroupId
					});

			$scope.modelNew = function() {
				var $ctrl = this;
				$scope.model = {
					dataGroupId : $stateParams.mlDataGroupId
				};
				var modalInstance = $uibModal.open({
					animation : true,
					ariaLabelledBy : 'modal-title',
					ariaDescribedBy : 'modal-body',
					templateUrl : 'templates/ml/model/ml-model-new.html',
					controller : 'TurMLModelNewCtrl',
					controllerAs : '$ctrl',
					size : null,
					appendTo : undefined,
					resolve : {
						model : function() {
							return $scope.model;
						}
					}
				});

				modalInstance.result.then(function(response) {					
					//
				}, function() {
					// Selected NO
				});

			}

		} ]);