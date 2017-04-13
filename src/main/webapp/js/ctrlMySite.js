var vigletApp = angular.module('vigletApp', []);

vigletApp.controller('SearchMySiteCtrl', function($scope, $http) {
	$scope.init = function(q) {
		$scope.qFormat = q;
		$scope.qHTML = "";
		if ($scope.qFormat === null || $scope.qFormat.length < 1 ) {
			$scope.qFormat = "*:*";
			$scope.qHTML = "";
		} else {
			$scope.qHTML = $scope.qFormat;
		}
		$http.get(
				"/turing/api/search?q="
						+ $scope.qFormat).success(function(data) {
			$scope.vigResults = data;
		});
	}
});