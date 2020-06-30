turingApp.factory('turConverseIntentFactory', [
    '$uibModal',
    function ($uibModal) {
        return {
            showParamPrompts: function (actionName, parameter) {
                var modalInstance = this.modalParamPrompts(actionName, parameter);
                modalInstance.result.then(function (promptsModal) {
                    parameter.prompts = promptsModal;
                }, function () {
                    // Selected NO
                });
            },
            modalParamPrompts: function (actionName, parameter) {
                var resolvePrompt = {
                    actionName : actionName,
                    parameter: parameter
                }
                var $ctrl = this;
                return $uibModal.open({
                    animation: true
                    , ariaLabelledBy: 'modal-title'
                    , ariaDescribedBy: 'modal-body'
                    , templateUrl: 'templates/converse/intent/converse-param-prompts.html'
                    , controller: 'TurModalConversePromptListCtrl'
                    , controllerAs: '$ctrl'
                    , size: null
                    , appendTo: undefined
                    , resolve: {
                        resolvePrompt: function () {
                            return resolvePrompt;
                        }                  
                    }
                });
            }
        }
    }]);