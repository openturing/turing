turingApp.controller('TurMLDataSentenceCtrl', [
		"$scope",
		"$stateParams",
		"$state",
		"$rootScope",
		"$translate",
		"vigLocale",
		"$uibModal",
		"turMLDataGroupCategoryResource",
		"turMLDataGroupSentenceResource",
		"turNotificationService",
		function($scope, $stateParams, $state, $rootScope, $translate,
				vigLocale, $uibModal, turMLDataGroupCategoryResource,
				turMLDataGroupSentenceResource, turNotificationService) {

			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);
			$rootScope.$state = $state;
			$scope.sentenceUpdate = function(turDataGroupSentence) {
				turMLDataGroupSentenceResource.update({
					dataGroupId : $stateParams.mlDataGroupId,
					id : turDataGroupSentence.id
				}, turDataGroupSentence, function() {
					turNotificationService.addNotification("Sentence \""
							+ turDataGroupSentence.sentence.substring(0, 20)
							+ "...\" was saved.");
				});
			}
		} ]);