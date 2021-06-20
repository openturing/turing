turingApp.controller('TurConverseTrainingListCtrl', [
    "$scope",
    "$filter",
    "turConverseTrainingResource",
    function ($scope, $filter, turConverseTrainingResource) {
        $scope.conversations = turConverseTrainingResource.query( function() {
            $scope.conversations = $filter('orderBy')($scope.conversations, '-date');
        });        
    }]);