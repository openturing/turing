turingApp.controller('TurModalConversePromptListCtrl', [
	"$uibModalInstance",
	"resolvePrompt",	
	"$filter",
	function ($uibModalInstance, resolvePrompt, $filter) {
		var $ctrl = this;
		$ctrl.actionName = resolvePrompt.actionName;
		$ctrl.prompts = $filter('orderBy')(resolvePrompt.parameter.prompts, 'position');
		$ctrl.i = 0;	
		$ctrl.parameter = resolvePrompt.parameter;
		$ctrl.addPrompt = function () {
			$ctrl.i = $ctrl.i + 1;
			var promptObject = {
				position: $ctrl.parameter.prompts.length + 1,
				text: ""
			}
			$ctrl.prompts.push(promptObject);

		}

		$ctrl.removePrompt = function (index) {
			$ctrl.i= $ctrl.i - 1;
			$ctrl.prompts.splice(index, 1);
		}

		$ctrl.ok = function () {
			$uibModalInstance.close($ctrl.prompts);
		};
	}]);