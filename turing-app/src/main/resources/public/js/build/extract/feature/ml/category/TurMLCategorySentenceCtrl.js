turingApp.controller('TurMLCategorySentenceCtrl', [
		"$scope",
		"$stateParams",
		"$state",
		"$rootScope",
		"$translate",
		"vigLocale",
		"$uibModal",
		"turMLDataSentenceResource",
		"turNotificationService",
		function($scope, $stateParams, $state, $rootScope, $translate,
				vigLocale, $uibModal, turMLDataSentenceResource,
				turNotificationService) {

			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);
			$rootScope.$state = $state;
			$scope.sentenceNew = function() {
				var $ctrl = this;
				$scope.sentence = {
					dataGroupId : $stateParams.mlDataGroupId,
					turMLCategoryId : $stateParams.mlCategoryId
				};
				$scope.categoryId = {
						dataGroupId : $stateParams.mlDataGroupId
					};
				var modalInstance = $uibModal.open({
					animation : true,
					ariaLabelledBy : 'modal-title',
					ariaDescribedBy : 'modal-body',
					templateUrl : 'templates/ml/sentence/ml-sentence-new.html',
					controller : 'TurMLSentenceNewCtrl',
					controllerAs : '$ctrl',
					size : null,
					appendTo : undefined,
					resolve : {
						sentence : function() {
							return $scope.sentence;
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