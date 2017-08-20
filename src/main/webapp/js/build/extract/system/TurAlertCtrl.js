turingApp.controller('TurAlertCtrl', [ "$scope", "turNotificationService",
		function($scope, turNotificationService) {
		$scope.alerts = turNotificationService.notifications;

			$scope.closeAlert = function(index) {
				turNotificationService.notifications.splice(index, 1);
			};
		} ]);