turingApp.controller('TurConverseHistoryCtrl', [
    "$scope",
    "$filter",
    "turConverseHistoryResource",
    function ($scope, $filter, turConverseHistoryResource) {
        $scope.conversations = turConverseHistoryResource.query( function() {
            $scope.conversations = $filter('orderBy')($scope.conversations, '-date');
        });    

        $scope.removeConversation = function (conversation, index) {
			turConverseHistoryResource.delete({ id: conversation.id });
			$scope.conversations.splice(index, 1);
		}
    }]);