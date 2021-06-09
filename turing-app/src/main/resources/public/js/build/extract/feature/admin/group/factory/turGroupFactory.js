turingApp.factory('turGroupFactory', [
	'$uibModal', 'turGroupResource', 'Notification', '$state',
	function ($uibModal, turGroupResource, Notification, $state) {
		const varToString = varObj => Object.keys(varObj)[0];
		return {
			delete: function (turGroup) {
				var modalInstance = this.modalDelete(turGroup);
				modalInstance.result.then(function (removeInstance) {
					var deletedMessage = 'The ' + turGroup.name + ' was deleted.';

					turGroupResource
						.delete({
							id: turGroup.id
						}, function () {
							Notification.error(deletedMessage);
							$state.go('admin.group', {}, { reload: true });
						});
				}, function () {
					// Selected NO
				});
			},
			save: function (turGroup) {
				if (turGroup.id > 0) {
					var updateMessage = 'The ' + turGroup.name + ' was saved.';
					turGroup.$update(function () {
						Notification.warning(updateMessage);
						$state.go('admin.group');
					});
				} else {
					var saveMessage = 'The ' + turGroup.name + ' was updated.';
					delete turGroup.id;
					turGroupResource.save(turGroup, function (response) {
						Notification.warning(saveMessage);
						$state.go('admin.group');
					});
				}
			}, addUsers: function (turGroup) {
				var modalInstance = this.modalSelectUser(turGroup);
				modalInstance.result.then(function (turUsers) {
					if (turGroup.turUsers != null) {
						angular.forEach(turUsers, function (turUser, key) {
							console.log(turUser.username);							
							turGroup.turUsers.putur(turUser);
						});
					}
					else {
						turGroup.turUsers = turUsers;
					}
				}, function () {
					// Selected NO
				});
			},
			modalDelete: function (turGroup) {
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
							return turGroup.name;
						}
					}
				});
			},
			modalSelectUser: function (turGroup) {
				var $ctrl = this;
				return $uibModal.open({
					animation: true
					, ariaLabelledBy: 'modal-title'
					, ariaDescribedBy: 'modal-body'
					, templateUrl: 'template/admin/user/user-select-dialog.html'
					, controller: 'TurModalSelectUserListCtrl'
					, controllerAs: varToString({ $ctrl })
					, size: null
					, appendTo: undefined
					, resolve: {
						groupId: function () {
							return turGroup.id;
						}
					}
				});
			}
		}
	}]);
