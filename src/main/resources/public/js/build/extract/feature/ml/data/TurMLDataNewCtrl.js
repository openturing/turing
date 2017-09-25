turingApp.controller('TurMLDataNewCtrl', [ "$uibModalInstance",
	"data", 'fileUpload', 'turNotificationService', function($uibModalInstance, data, fileUpload, turNotificationService) {
		var $ctrl = this;
		$ctrl.myFile = null;
		$ctrl.removeInstance = false;
		$ctrl.data = data;
		$ctrl.ok = function() {
			var file = $ctrl.myFile;
			var uploadUrl = '/turing/api/ml/data/group/' + data.datagroupId + '/data/import';
			var response = null;
			fileUpload.uploadFileToUrl(file, uploadUrl).then( function(response){
				turNotificationService.addNotification(response.data.turData.name + "\" file was uploaded.");
				$uibModalInstance.close(response);
			});
			
			
		};

		$ctrl.cancel = function() {
			$ctrl.removeInstance = false;
			$uibModalInstance.dismiss('cancel');
		};
	} ]);