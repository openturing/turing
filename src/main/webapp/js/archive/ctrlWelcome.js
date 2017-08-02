var vigletApp = angular.module('vigletApp', [ "directive.g+signin" ]);

vigletApp.controller('WelcomeController', function($scope, $http, $window) {
	$scope.$on('event:google-plus-signin-success', function(event, authResult) {
		// User successfully authorized the G+ App!
		console.log('Signed in!');
		console.log('authResult', authResult);

		postData = authResult['code'];
		$http({
			method : 'POST',
			url : '/turing/connect',
			data : postData, // forms user object
			headers : {
				'Content-Type' : 'application/x-www-form-urlencoded'
			}
		}).success(function(data, status, headers, config) {
			console.log(data);
			console.log('Conectado!');
			url = '../#/welcome';
			$window.location.href = url;

		});

	});
	$scope.$on('event:google-plus-signin-failure', function(event, authResult) {
		// User has not authorized the G+ App!
		console.log('Not signed into Google Plus.');
	});
});