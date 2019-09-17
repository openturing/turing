turConverseApp
	.controller(
		'TurConverseMainCtrl',
		[
			"$scope",
			"$translate",
			"amMoment",
			"vigLocale",
			"$http",
			"turAPIServerService",
			"$timeout",
			"$location", 
			"$anchorScroll",
			function ($scope, $translate, amMoment, vigLocale, $http, turAPIServerService, $timeout, $location, $anchorScroll) {
				$scope.waiting = false;
				$scope.vigLanguage = vigLocale.getLocale()
					.substring(0, 2);
				$translate.use($scope.vigLanguage);
				amMoment.changeLocale('en');

				$scope.turConverseText = "";
				$scope.messages = [];

				var message = {
					"bot": false,
					"text": "Olá tudo bem?"
				};

				$scope.messages.push(message)

				var messagebot = {
					"bot": true,
					"text": "Tudo e vc?"
				}
				$scope.messages.push(messagebot);

				$scope.sendMessage = function () {
					var message = {
						"bot": false,
						"text": $scope.turConverseText
					};
					$scope.messages.push(message);
					$location.hash('bottom');
					$anchorScroll();

					$scope.waiting = true;
					$scope
						.$evalAsync($http
							.get(turAPIServerService.get().concat("/converse/agent/try?q=" + $scope.turConverseText))
							.then(
								function (response) {
									if (response !== null) {
										$scope.agentResponse = response.data;
										var message = {
											"bot": true,
											"text": $scope.agentResponse.response
										};
										$timeout(function () {
											$scope.waiting = false;
											$scope.messages.push(message);
											$location.hash('bottom');
											$anchorScroll();
										}, 50);

									}
								}));

					$scope.turConverseText = "";
				}
			}]);