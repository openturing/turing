turingApp.controller('TurConverseConversationCtrl', [
    "$scope",
    "$stateParams",
    "turConverseHistoryResource",
    function ($scope, $stateParams, turConverseHistoryResource) {
        $scope.conversationId = $stateParams.conversationId;
        $scope.conversation = turConverseHistoryResource.get({
            id: $stateParams.conversationId
        });
    }]);