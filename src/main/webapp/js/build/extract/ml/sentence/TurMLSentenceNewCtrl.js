turingApp.controller('TurMLSentenceNewCtrl', [
		"$uibModalInstance",
		"sentence",
		"turMLDataGroupSentenceResource",
		"turNotificationService",
		function($uibModalInstance, sentence, turMLDataGroupSentenceResource,
				turNotificationService) {
			var $ctrl = this;
			$ctrl.removeInstance = false;
			$ctrl.dataGroupId = sentence.dataGroupId;
			$ctrl.sentence = sentence;
			$ctrl.ok = function() {
				delete sentence.dataGroupId;

				turMLDataGroupSentenceResource.save({
					dataGroupId : $ctrl.dataGroupId
				}, $ctrl.sentence, function(response) {
					turNotificationService.addNotification("Sentence \""
							+ response.sentence + "\" was created.");
					$uibModalInstance.close(response);
				});

			};

			$ctrl.cancel = function() {
				$uibModalInstance.dismiss('cancel');
			};
		} ]);