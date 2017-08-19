turingApp.controller('TurMLDataEditCtrl', [
		"$scope",
		"$stateParams",
		"$state",
		"$rootScope",
		"$translate",
		"vigLocale",
		"turMLDataGroupResource",
		"$uibModal",
		function($scope, $stateParams, $state, $rootScope, $translate,
				vigLocale, turMLDataGroupResource, $uibModal) {

			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);
			$rootScope.$state = $state;

			$scope.dataGroup = turMLDataGroupResource.get({
				id : $stateParams.mlDataGroupId
			});
			$scope.dataGroupSave = function() {
				$scope.dataGroup.$update(function() {
					$state.go('ml.datagroup');
				});
			}

			$scope.uploadDocument = function() {
				var $ctrl = this;
				$scope.data = {}
				var modalInstance = $uibModal.open({
					animation : true,
					ariaLabelledBy : 'modal-title',
					ariaDescribedBy : 'modal-body',
					templateUrl : 'templates/ml/data/ml-document-upload.html',
					controller : 'TurMLDataNewCtrl',
					controllerAs : '$ctrl',
					size : null,
					appendTo : undefined,
					resolve : {
						data : function() {
							return $scope.data;
						}
					}
				});

				modalInstance.result.then(function(data) {
					console.log(data.name);
					console.log(data.description);
				}, function() {
					// Selected NO
				});

			}

		} ]);