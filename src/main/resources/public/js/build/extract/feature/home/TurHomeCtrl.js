turingApp.controller('TurHomeCtrl', [ "$scope", "$http", "$window", "$state",
		"$rootScope", "$translate",
		function($scope, $http, $window, $state, $rootScope, $translate) {
			$scope.accesses = null;
			$rootScope.$state = $state;
		} ]);