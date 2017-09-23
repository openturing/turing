turingApp.controller('TurMLInstanceCtrl',
		[
				"$scope",
				"$state",
				"$rootScope",
				"$translate",
				"turMLInstanceResource",
				function($scope, $state, $rootScope, $translate,
						turMLInstanceResource) {

					$rootScope.$state = $state;
					$scope.mls = turMLInstanceResource.query();
				} ]);