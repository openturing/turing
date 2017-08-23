turingApp.controller('TurMLDataSentenceCtrl', [
		"$scope",
		"$stateParams",
		"$state",
		"$rootScope",
		"$translate",
		"vigLocale",
		"$uibModal",
		"turMLCategoryResource",
		"turMLDataSentenceResource",
		"turNotificationService",
		function($scope, $stateParams, $state, $rootScope, $translate,
				vigLocale, $uibModal, turMLCategoryResource,
				turMLDataSentenceResource, turNotificationService) {

			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);
			$rootScope.$state = $state;
			$scope.categories = turMLCategoryResource.query();
			$scope.sentenceUpdate = function(turDataSentence) {
				turMLDataSentenceResource.update({
					id : turDataSentence.id
				}, turDataSentence, function() {
					turNotificationService.addNotification("Sentence \""
							+ turDataSentence.sentence.substring(0,20) + "...\" was saved.");
				});
			}
		} ]);