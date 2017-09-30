turingApp.controller('TurHomeCtrl', [ "$scope", "$http", "$window", "$state",
		"$rootScope", "$translate",'turAPIServerService',
		function($scope, $http, $window, $state, $rootScope, $translate, turAPIServerService) {
			createServerAPICookie = turAPIServerService.get();
			$scope.accesses = null;
			$rootScope.$state = $state;
		} ]);