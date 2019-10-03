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
			"$stateParams",
			"turConverseAgentResource",
			function ($scope, $translate, amMoment, vigLocale, $http, turAPIServerService, $timeout, $location, $anchorScroll, $stateParams, turConverseAgentResource) {
			
				$scope.agentId = $stateParams.agentId;
				$scope.agent = turConverseAgentResource.get({id: $stateParams.agentId});	
				$scope.waiting = false;
				$scope.vigLanguage = vigLocale.getLocale()
					.substring(0, 2);
				$translate.use($scope.vigLanguage);
				amMoment.changeLocale('en');

				$scope.turConverseText = "";
				$scope.messages = [];

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
							.get(turAPIServerService.get().concat("/converse/agent/" + $scope.agentId + "/chat?q=" + $scope.turConverseText))
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