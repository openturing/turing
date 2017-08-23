turingApp.controller('TurMLCategoryNewCtrl', [
		"$uibModalInstance",
		"category",
		"turMLCategoryResource",
		"turNotificationService",
		function($uibModalInstance, category, turMLCategoryResource,
				turNotificationService) {
			var $ctrl = this;
			$ctrl.removeInstance = false;
			$ctrl.category = category;
			$ctrl.ok = function() {
				turMLCategoryResource.save($ctrl.category, function() {
					turNotificationService.addNotification("Category \""
							+ $ctrl.category.name + "\" was created.");
					$uibModalInstance.close(category);
				});

			};

			$ctrl.cancel = function() {
				$uibModalInstance.dismiss('cancel');
			};
		} ]);