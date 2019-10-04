turingApp.controller('TurConverseTrainingCtrl', [
    "$scope",
    "$stateParams",
    "$http",
    "turConverseTrainingResource",
    "turAPIServerService",
    function ($scope, $stateParams, $http, turConverseTrainingResource, turAPIServerService) {
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
    }]);