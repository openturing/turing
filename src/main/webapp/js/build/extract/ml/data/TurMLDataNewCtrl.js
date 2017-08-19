turingApp.controller('TurMLDataNewCtrl', [ "$uibModalInstance",
	"data", 'fileUpload', function($uibModalInstance, data, fileUpload) {
		var $ctrl = this;
		$ctrl.myFile = null;
		$ctrl.removeInstance = false;
		$ctrl.data = data;
		$ctrl.ok = function() {
			var file = $ctrl.myFile;
			var uploadUrl = '/turing/api/ml/data/import';
			fileUpload.uploadFileToUrl(file, uploadUrl);
			$uibModalInstance.close(data);
		};

		$ctrl.cancel = function() {
			$ctrl.removeInstance = false;
			$uibModalInstance.dismiss('cancel');
		};
	} ]);