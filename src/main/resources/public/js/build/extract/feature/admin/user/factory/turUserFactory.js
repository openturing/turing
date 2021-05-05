turingApp.factory('turUserFactory', [
	'$uibModal', 'turUserResource', 'Notification', '$state',
	function ($uibModal, turUserResource, Notification, $state) {
		const varToString = varObj => Object.keys(varObj)[0];
		return {
			delete: function (turUser) {
				var modalInstance = this.modalDelete(turUser);
				modalInstance.result.then(function (removeInstance) {
					var deletedMessage = 'The ' + turUser.username + ' was deleted.';

					turUserResource
						.delete({
							id: turUser.username
						}, function () {
							Notification.error(deletedMessage);
							$state.go('admin.user', {}, { reload: true });
						});
				}, function () {
					// Selected NO
				});
			},
			save: function (turUser, isNew) {
				if (!isNew) {
					var updateMessage = 'The ' + turUser.username + ' was updated.';
					turUser.$update(function () {
						Notification.warning(updateMessage);
						$state.go('admin.user');
					});
				} else {
					var saveMessage = 'The ' + turUser.username + ' was saved.';
					turUserResource.save(turUser, function (response) {
						Notification.warning(saveMessage);
						isNew = false;
						$state.go('admin.user');
					});
				}
			},
			addGroups: function (turUser) {
				var modalInstance = this.modalSelectGroup(turUser);
				modalInstance.result.then(function (turGroups) {
					if (turUser.turGroups != null) {
						angular.forEach(turGroups, function (turGroup, key) {
							console.log(turGroup.name);
							turUser.turGroups.putur(turGroup);
						});						
					}
					else {
						turUser.turGroups = turGroups;
					}
				}, function () {
					// Selected NO
				});
			}, modalDelete: function (turUser) {
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
							return turUser.username;
						}
					}
				});
			},
			modalSelectGroup: function (turUser) {
				var $ctrl = this;
				return $uibModal.open({
					animation: true
					, ariaLabelledBy: 'modal-title'
					, ariaDescribedBy: 'modal-body'
					, templateUrl: 'template/admin/group/group-select-dialog.html'
					, controller: 'TurModalSelectGroupListCtrl'
					, controllerAs: varToString({ $ctrl })
					, size: null
					, appendTo: undefined
					, resolve: {
						username: function () {
							return turUser.username;
						}
					}
				});
			}
		}
	}]);
