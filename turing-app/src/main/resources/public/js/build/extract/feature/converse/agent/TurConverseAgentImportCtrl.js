turingApp.controller('TurConverseAgentImportCtrl', [
		'$scope',
		'$state',
		'Upload',
		'$timeout',
		'turAPIServerService',
		'Notification',
		function($scope, $state, Upload, $timeout, turAPIServerService,
				Notification) {
			$scope.agentImport = {
				file : null
			};

			$scope.$watch('agentImport.file', function() {
				//
			});

			$scope.clearFile = function() {
				$scope.agentImport.file = null;
			}
			$scope.importFile = function() {
				if (!$scope.agentImport.file.$error) {
					Upload.upload({
						url : turAPIServerService.get().concat('/converse/agent/import'),
						data : {
							file : $scope.agentImport.file
						}
					}).then(

							function(resp) {
								if (typeof resp.data.sites != 'undefined') {
									var agentName = resp.data.agent[0].name;
									Notification.warning('The ' + agentName
											+ ' Agent was imported.');
								} else {
									Notification
											.warning('Objects were imported.');
								}
								$state.go('converse.agent-list');
							},
							null,
							function(evt) {
								var progressPercentage = parseInt(100.0
										* evt.loaded / evt.total);
							});
				}
			}
		} ]);
