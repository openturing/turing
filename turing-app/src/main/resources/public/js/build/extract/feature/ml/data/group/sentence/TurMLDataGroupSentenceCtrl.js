turingApp.controller('TurMLDataGroupSentenceCtrl', [
		"$scope",
		"$stateParams",
		"$state",
		"$rootScope",
		"$translate",
		"vigLocale",
		"turMLDataGroupSentenceResource",
		"$uibModal",
		"turNotificationService",
		function($scope, $stateParams, $state, $rootScope, $translate,
				vigLocale, turMLDataGroupSentenceResource, $uibModal, turNotificationService) {

			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);
			$rootScope.$state = $state;

			$scope.mlDataGroupSentences = turMLDataGroupSentenceResource
					.query({
						dataGroupId : $stateParams.mlDataGroupId
					});

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
			
			$scope.sentenceNew = function() {
				var $ctrl = this;
				$scope.sentence = {
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