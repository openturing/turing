turingApp
		.controller(
				'TurSNSiteFieldCtrl',
				[
						"$scope",
						"$http",
						"$window",
						"$state",
						"$rootScope",
						"$translate",
						"$uibModal",
						"$stateParams",
						"turSNSiteFieldResource",
						"turSNSiteFieldExtResource",
						"turNLPEntityLocalResource",
						"turNotificationService",
						"$filter",
						function($scope, $http, $window, $state, $rootScope,
								$translate, $uibModal, $stateParams,
								turSNSiteFieldResource,
								turSNSiteFieldExtResource,
								turNLPEntityLocalResource,
								turNotificationService, $filter) {
							$rootScope.$state = $state;

							$scope.turSNSiteFieldExts = turSNSiteFieldExtResource
									.query({
										snSiteId : $stateParams.snSiteId
									});

							$scope.snSiteFieldUpdate = function(snSiteFieldExt) {
								if (snSiteFieldExt != null) {
									turSNSiteFieldExtResource.update({
										id : snSiteFieldExt.id,
										snSiteId : $stateParams.snSiteId
									}, snSiteFieldExt, function() {
										//
									});
								}
							}
							$scope.fieldNew = function() {
								var $ctrl = this;
								$scope.snSiteField = {};
								var modalInstance = $uibModal
										.open({
											animation : true,
											ariaLabelledBy : 'modal-title',
											ariaDescribedBy : 'modal-body',
											templateUrl : 'templates/sn/site/field/sn-site-field-new.html',
											controller : 'TurSNSiteFieldNewCtrl',
											controllerAs : '$ctrl',
											size : null,
											appendTo : undefined,
											resolve : {
												snSiteField : function() {
													return $scope.snSiteField;
												},
												snSiteId : function() {
													return $stateParams.snSiteId;
												}
											}
										});

								modalInstance.result.then(function(response) {
									//
								}, function() {
									// Selected NO
								});

							}
						} ]);