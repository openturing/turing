turingApp.service('turNotificationService', [ '$http', function($http) {
	this.notifications = [];
	this.addNotification = function(msgString) {
		this.notifications.push({
			msg : msgString
		});
	};

} ]);