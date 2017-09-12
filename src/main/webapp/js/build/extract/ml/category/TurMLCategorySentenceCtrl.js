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
				vigLocale, $uibModal,
				turMLDataSentenceResource, turNotificationService) {

			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);
			$rootScope.$state = $state;
			$scope.sentenceUpdate = function(turDataSentence) {
				turMLDataSentenceResource.update({
					id : turDataSentence.id
				}, turDataSentence, function() {
					turNotificationService.addNotification("Sentence \""
							+ turDataSentence.sentence.substring(0,20) + "...\" was saved.");
				});
			}
		} ]);