turingApp.factory('turRoleFactory', [
	'$uibModal','turRoleResource', 'Notification','$state',
		function($uibModal,turRoleResource, Notification, $state) {
			const varToString = varObj => Object.keys(varObj)[0];
			return {
				delete : function(turRole) {
					var modalInstance = this.modalDelete(turRole);
					modalInstance.result.then(function(removeInstance) {
						var deletedMessage = 'The ' + turRole.name +' was deleted.';
						
						turRoleResource
						.delete({
							id : turRole.id
						},function() {
							Notification.error(deletedMessage);
							$state.go('admin.role',{}, {reload: true});
						});
					}, function() {
						// Selected NO
					});
				}, 			
				save: function(turRole) {
					if (turRole.id > 0 ) {
						var updateMessage = 'The ' + turRole.name +' was saved.';
						turRole.$update(function() {
							Notification.warning(updateMessage);
							$state.go('admin.role');						
						});
					} else {
						var saveMessage = 'The ' + turRole.name +' was updated.';
						delete turRole.id;
						turRoleResource.save(turRole, function(response){
							Notification.warning(saveMessage);
							$state.go('admin.role');
						});
					}	
				},
				modalDelete: function (turRole) {
					var $ctrl = this;
					return $uibModal.open({
						animation: true
						, ariaLabelledBy: 'modal-title'
						, ariaDescribedBy: 'modal-body'
						, templateUrl: 'template/admin/user/user-delete.html'
						, controller: 'TurModalDeleteUserCtrl'
						, controllerAs: varToString({ $ctrl })
						, size: null
						, appendTo: undefined
						, resolve: {
							title: function () {
								return turRole.name;
							}
						}
					});
				}					
			}
		} ]);
