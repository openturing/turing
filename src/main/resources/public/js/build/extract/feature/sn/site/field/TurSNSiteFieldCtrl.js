turingApp.controller('TurSNSiteFieldCtrl', [
		"$scope",
		"$http",
		"$window",
		"$state",
		"$rootScope",
		"$translate",
		"$uibModal",
		"$stateParams",
		"turSNSiteFieldResource",
		"turNotificationService",
		function($scope, $http, $window, $state, $rootScope, $translate, $uibModal, $stateParams,turSNSiteFieldResource, turNotificationService) {
			$rootScope.$state = $state;
			
			$scope.snSiteFieldUpdate = function(snSiteField) {			
				turSNSiteFieldResource.update({
					id:	snSiteField.id,		
					snSiteId : $stateParams.snSiteId
				}, snSiteField, function() {
					//turNotificationService.addNotification("Field \"" + snSiteField.name + "\" was updated.");
				});
			}
			$scope.fieldNew = function() {
				var $ctrl = this;
				$scope.snSiteField = {};
				var modalInstance = $uibModal.open({
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
							return  $stateParams.snSiteId;
						}
					}
				});
				
				modalInstance.result.then(function(response) {
					/*delete response.turDataGroupCategories;
					delete response.turDataSentences;
					turMLDataGroupCategory = {};
					turMLDataGroupCategory.turMLCategory = response;
					turMLDataGroupCategoryResource.save({
						dataGroupId : $stateParams.mlDataGroupId
					}, turMLDataGroupCategory);*/

					//
				}, function() {
					// Selected NO
				});

			}
		} ]);