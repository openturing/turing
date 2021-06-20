turingApp.controller('TurSNSiteFieldNewCtrl', [
		"$uibModalInstance",
		"snSiteFieldExt",
		"snSiteId",
		"turSNSiteFieldExtResource",
		"turNotificationService",
		function($uibModalInstance, snSiteFieldExt, snSiteId,
				turSNSiteFieldExtResource, turNotificationService) {
			var $ctrl = this;
			$ctrl.removeInstance = false;
			$ctrl.snSiteFieldExt = snSiteFieldExt;
			$ctrl.ok = function() {
				console.log($ctrl.snSiteField);
				turSNSiteFieldExtResource.save({
					snSiteId : snSiteId
				}, $ctrl.snSiteFieldExt, function(response) {
					turNotificationService.addNotification("Field \""
							+ response.name + "\" was created.");
					$uibModalInstance.close(response);
				});

			};

			$ctrl.cancel = function() {
				$uibModalInstance.dismiss('cancel');
			};
		} ]);