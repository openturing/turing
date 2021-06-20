turingApp.controller('TurModalSelectUserListCtrl', [
	"$uibModalInstance",
	"groupId",
	"turUserResource",
	function ($uibModalInstance, groupId, turUserResource) {
		var $ctrl = this;
		$ctrl.groupId = groupId;
		$ctrl.checkAll = false;
		$ctrl.turStateObjects = [];
		$ctrl.turObjects = [];
		$ctrl.turUsers = turUserResource.query({}, function () {
			angular.forEach($ctrl.turUsers, function (turUser, key) {
				$ctrl.turStateObjects[turUser.username] = false;
				$ctrl.turObjects[turUser.username] = turUser;
			});
		});

		$ctrl.itemSelected = false;
		$ctrl.ok = function () {			
			var objects = [];
            for (var stateKey in $ctrl.turStateObjects) {
				console.log($ctrl.turStateObjects[stateKey]);
                if ($ctrl.turStateObjects[stateKey] === true) {
					console.log("Add");
					objects.putur($ctrl.turObjects[stateKey]);
                }
			}
			angular.forEach(objects, function (turUser, key) {
				console.log(turUser.name)
			});
			$uibModalInstance.close(objects);
		};

		$ctrl.cancel = function () {
			$ctrl.removeInstance = false;
			$uibModalInstance.dismiss('cancel');
		};

		$ctrl.checkSomeItemSelected = function () {
			$ctrl.itemSelected = false;
			for (var stateKey in $ctrl.turStateObjects) {
				if ($ctrl.turStateObjects[stateKey]) {
					$ctrl.itemSelected = true;
				}
			}
		}
		$ctrl.selectEverything = function () {
			if ($ctrl.checkAll) {
				for (var stateKey1 in $ctrl.turStateObjects) {
					$ctrl.turStateObjects[stateKey1] = true;
				}
			}
			else {
				for (var stateKey2 in $ctrl.turStateObjects) {
					$ctrl.turStateObjects[stateKey2] = false;
				}
			}
			$ctrl.checkSomeItemSelected();
		}
	}]);
