turingApp.controller('TurMLDataGroupCategoryCtrl', [
		"$scope",
		"$stateParams",
		"$state",
		"$rootScope",
		"$translate",
		"vigLocale",
		"turMLCategoryResource",
		"$uibModal",
		function($scope, $stateParams, $state, $rootScope, $translate,
				vigLocale, turMLCategoryResource, $uibModal) {

			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);
			$rootScope.$state = $state;

			$scope.categories = turMLCategoryResource.query();

			$scope.categoryNew = function() {
				var $ctrl = this;
				$scope.category = {}
				var modalInstance = $uibModal.open({
					animation : true,
					ariaLabelledBy : 'modal-title',
					ariaDescribedBy : 'modal-body',
					templateUrl : 'templates/ml/category/ml-category-new.html',
					controller : 'TurMLCategoryNewCtrl',
					controllerAs : '$ctrl',
					size : null,
					appendTo : undefined,
					resolve : {
						category : function() {
							return $scope.category;
						}
					}
				});

				modalInstance.result.then(function(category) {
					//
				}, function() {
					// Selected NO
				});

			}

		} ]);