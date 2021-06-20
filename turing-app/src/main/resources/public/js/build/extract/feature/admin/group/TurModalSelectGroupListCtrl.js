turingApp.controller('ShModalSelectGroupListCtrl', [
	"$uibModalInstance",
	"username",
	"turGroupResource",
	function ($uibModalInstance, username, turGroupResource) {
		var $ctrl = this;
		$ctrl.username = username;
		$ctrl.checkAll = false;
		$ctrl.turStateObjects = [];
		$ctrl.turObjects = [];
		$ctrl.turGroups = turGroupResource.query({}, function () {
			angular.forEach($ctrl.turGroups, function (turGroup, key) {
				$ctrl.turStateObjects[turGroup.id] = false;
				$ctrl.turObjects[turGroup.id] = turGroup;
			});
		});

		$ctrl.itemSelected = false;
		$ctrl.ok = function () {			
			var objects = [];
            for (var stateKey in $ctrl.turStateObjects) {
				console.log($ctrl.turStateObjects[stateKey]);
                if ($ctrl.turStateObjects[stateKey] === true) {
					objects.putur($ctrl.turObjects[stateKey]);
                }
			}
			angular.forEach(objects, function (turGroup, key) {
				console.log(turGroup.name)
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
