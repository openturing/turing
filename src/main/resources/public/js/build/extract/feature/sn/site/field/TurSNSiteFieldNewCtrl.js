turingApp.controller('TurSNSiteFieldNewCtrl', [
		"$uibModalInstance",
		"snSiteField",
		"snSiteId",
		"turSNSiteFieldResource",
		"turNotificationService",
		function($uibModalInstance, snSiteField, snSiteId,
				turSNSiteFieldResource, turNotificationService) {
			var $ctrl = this;
			$ctrl.removeInstance = false;
			$ctrl.snSiteField = snSiteField;
			console.log($ctrl.snSiteField);
			$ctrl.ok = function() {
				console.log($ctrl.snSiteField);
				turSNSiteFieldResource.save({
					snSiteId : snSiteId
				}, $ctrl.snSiteField, function(response) {
					turNotificationService.addNotification("Field \""
							+ response.name + "\" was created.");
					$uibModalInstance.close(response);
				});

			};

			$ctrl.cancel = function() {
				$uibModalInstance.dismiss('cancel');
			};
		} ]);