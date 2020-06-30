turingApp.controller('TurConverseTrainingCtrl', [
    "$scope",
    "$stateParams",
    "$http",
    "turConverseTrainingResource",
    "turAPIServerService",
    "Notification",
    function ($scope, $stateParams, $http, turConverseTrainingResource, turAPIServerService, Notification) {
        $scope.intents = [];
        $scope
            .$evalAsync($http
                .get(turAPIServerService.get().concat("/converse/agent/" + $scope.agentId + "/intents"))
                .then(
                    function (response) {
                        $scope.intents = response.data;
                    }));

        $scope.conversationId = $stateParams.conversationId;
        $scope.conversation = turConverseTrainingResource.get({
            id: $stateParams.conversationId
        });

        $scope.removeResponse = function (response, index) {
            if (response.trainingRemove)
                response.trainingRemove = false;
            else {
                response.trainingToFallback = false;
                response.trainingToIntent = false;
                response.trainingRemove = true;
            }
            // $scope.conversation.responses.splice(index, 1);
            // Notification.error(response.text + " Response was removed");
        }

        $scope.saveTraining = function () {
            $scope.conversation.trainingApproved = true;
            turConverseTrainingResource.update({ id: $scope.conversation.id }, $scope.conversation, function (response) {
                Notification.warning("Training was updated");
            });
        }

        $scope.addToIntent = function (response, index) {
            if (response.trainingToIntent)
                response.trainingToIntent = false;
            else {
                response.trainingToFallback = false;
                response.trainingToIntent = true;
                response.trainingRemove = false;

            }
        }

        $scope.addToFallback = function (response, index) {
            if (response.trainingToFallback)
                response.trainingToFallback = false;
            else {
                response.trainingToFallback = true;
                response.trainingToIntent = false;
                response.trainingRemove = false;
            }
        }
    }]);