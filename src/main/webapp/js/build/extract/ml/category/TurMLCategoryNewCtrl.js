turingApp.controller('TurMLCategoryNewCtrl', [ "$uibModalInstance",
	"category", function($uibModalInstance, category) {
		var $ctrl = this;
		$ctrl.removeInstance = false;
		$ctrl.category = category;
		$ctrl.ok = function() {
			$ctrl.removeInstance = true;
			$uibModalInstance.close(category);
		};

		$ctrl.cancel = function() {
			$ctrl.removeInstance = false;
			$uibModalInstance.dismiss('cancel');
		};
	} ]);