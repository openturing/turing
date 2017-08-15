turingApp.config([
		'$stateProvider',
		'$urlRouterProvider',
		'$locationProvider',
		'$translateProvider',
		function($stateProvider, $urlRouterProvider, $locationProvider,
				$translateProvider) {
			$translateProvider.useSanitizeValueStrategy('escaped');
			$translateProvider.translations('en', {

				NLP_EDIT : "Edit NLP",
				NLP_EDIT_SUBTITLE : "Change the NLP Settings",
				NAME : "Name",
				DESCRIPTION : "Description",
				VENDORS : "Vendors",
				HOST : "Host",
				PORT : "Port",
				SETTINGS_SAVE_CHANGES : "Save Changes"
			});
			$translateProvider.translations('pt', {
				NLP_EDIT : "Editar o NLP",
				NLP_EDIT_SUBTITLE : "Altere as configurações do NLP",
				NAME : "Nome",
				DESCRIPTION : "Descrição",
				VENDORS : "Produtos",
				HOST : "Host",
				PORT : "Porta",
				SETTINGS_SAVE_CHANGES : "Salvar Alterações"
			});
			$translateProvider.fallbackLanguage('en');

			$urlRouterProvider.otherwise('/home');
			$stateProvider.state('home', {
				url : '/home',
				templateUrl : 'templates/home.html',
				controller : 'TurHomeCtrl',
				data : {
					pageTitle : 'Home | Viglet Turing'
				}
			}).state('ml', {
				url : '/ml',
				templateUrl : 'templates/ml/ml.html',
				data : {
					pageTitle : 'Machine Learning | Viglet Turing'
				}
			}).state('ml.instance', {
				url : '/instance',
				templateUrl : 'templates/ml/ml-instance.html',
				controller : 'TurMLInstanceCtrl',
				data : {
					pageTitle : 'Machine Learnings | Viglet Turing'
				}
			}).state('ml.instance-edit', {
				url : '/instance/:mlInstanceId',
				templateUrl : 'templates/ml/ml-instance-edit.html',
				controller : 'TurMLInstanceEditCtrl',
				data : {
					pageTitle : 'Edit Machine Learning | Viglet Turing'
				}
			}).state('ml.model', {
				url : '/model',
				templateUrl : 'templates/ml/model/ml-model.html',
				controller : 'TurMLModelCtrl',
				data : {
					pageTitle : 'Machine Learning Models | Viglet Turing'
				}
			}).state('ml.datagroup', {
				url : '/datagroup',
				templateUrl : 'templates/ml/data/group/ml-datagroup.html',
				controller : 'TurMLDataGroupCtrl',
				data : {
					pageTitle : 'Machine Learning Data Groups | Viglet Turing'
				}
			}).state('se', {
				url : '/se',
				templateUrl : 'templates/se/se.html',
				data : {
					pageTitle : 'Search Engine | Viglet Turing'
				}
			}).state('se.instance', {
				url : '/instance',
				templateUrl : 'templates/se/se-instance.html',
				controller : 'TurSEInstanceCtrl',
				data : {
					pageTitle : 'Search Engines | Viglet Turing'
				}
			}).state('se.instance-edit', {
				url : '/instance/:seInstanceId',
				templateUrl : 'templates/se/se-instance-edit.html',
				controller : 'TurSEInstanceEditCtrl',
				data : {
					pageTitle : 'Edit Search Engine | Viglet Turing'
				}
			}).state('se.sn', {
				url : '/sn',
				templateUrl : 'templates/se/sn/se-sn.html',
				controller : 'TurSESNCtrl',
				data : {
					pageTitle : 'Semantic Navigation | Viglet Turing'
				}
			}).state('nlp', {
				url : '/nlp',
				templateUrl : 'templates/nlp/nlp.html',
				data : {
					pageTitle : 'NLP | Viglet Turing'
				}
			}).state('nlp.instance', {
				url : '/instance',
				templateUrl : 'templates/nlp/nlp-instance.html',
				controller : 'TurNLPInstanceCtrl',
				data : {
					pageTitle : 'NLPs | Viglet Turing'
				}
			}).state('nlp.instance-edit', {
				url : '/instance/:nlpInstanceId',
				templateUrl : 'templates/nlp/nlp-instance-edit.html',
				controller : 'TurNLPInstanceEditCtrl',
				data : {
					pageTitle : 'Edit NLP | Viglet Turing'
				}
			}).state('nlp.validation', {
				url : '/validation',
				templateUrl : 'templates/nlp/nlp-validation.html',
				controller : 'TurNLPValidationCtrl',
				data : {
					pageTitle : 'NLP Validation | Viglet Turing'
				}
			}).state('nlp.entity', {
				url : '/entity',
				templateUrl : 'templates/nlp/entity/nlp-entity.html',
				controller : 'TurNLPEntityCtrl',
				data : {
					pageTitle : 'NLP Entities | Viglet Turing'
				}
			}).state('nlp.entity-import', {
				url : '/entity/import',
				templateUrl : 'templates/nlp/entity/nlp-entity-import.html',
				data : {
					pageTitle : 'Import Entity | Viglet Turing'
				}
			}).state('nlp.entity-edit', {
				url : '/entity/:nlpEntityId',
				templateUrl : 'templates/nlp/entity/nlp-entity-edit.html',
				controller : 'TurNLPEntityEditCtrl',
				data : {
					pageTitle : 'Edit Entity | Viglet Turing'
				}
			}).state('nlp.entity-edit.term', {
				url : '/term',
				templateUrl : 'templates/nlp/entity/nlp-entity-term.html',
				data : {
					pageTitle : 'Entity Terms | Viglet Turing'
				}
			}).state('organization.user', {
				url : '/user',
				templateUrl : 'user.html',
				controller : 'TurUserCtrl',
				data : {
					pageTitle : 'Users | Viglet Turing'
				}
			}).state('organization.user-new', {
				url : '/user/new',
				templateUrl : 'user-item.html',
				controller : 'TurUserNewCtrl',
				data : {
					pageTitle : 'New User | Viglet Turing'
				}
			}).state('organization.user-edit', {
				url : '/user/:userId',
				templateUrl : 'user-item.html',
				controller : 'TurUserEditCtrl',
				data : {
					pageTitle : 'Edit User | Viglet Turing'
				}
			}).state('organization.role', {
				url : '/role',
				templateUrl : 'role.html',
				controller : 'TurRoleCtrl',
				data : {
					pageTitle : 'Roles | Viglet Turing'
				}
			}).state('organization.role-new', {
				url : '/role/new',
				templateUrl : 'role-item.html',
				controller : 'TurRoleNewCtrl',
				data : {
					pageTitle : 'New Role | Viglet Turing'
				}
			}).state('organization.role-edit', {
				url : '/role/:roleId',
				templateUrl : 'role-item.html',
				controller : 'TurRoleEditCtrl',
				data : {
					pageTitle : 'Edit Role | Viglet Turing'
				}
			}).state('organization.group', {
				url : '/group',
				templateUrl : 'group.html',
				controller : 'TurGroupCtrl',
				data : {
					pageTitle : 'Groups | Viglet Turing'
				}
			}).state('organization.group-new', {
				url : '/group/new',
				templateUrl : 'group-item.html',
				controller : 'TurGroupNewCtrl',
				data : {
					pageTitle : 'New Group | Viglet Turing'
				}
			}).state('organization.group-edit', {
				url : '/group/:groupId',
				templateUrl : 'group-item.html',
				controller : 'TurGroupEditCtrl',
				data : {
					pageTitle : 'Edit Group | Viglet Turing'
				}
			}).state('app', {
				url : '/app',
				templateUrl : 'app.html',
				controller : 'TurAppCtrl',
				data : {
					pageTitle : 'Apps | Viglet Turing'
				}
			}).state('app-new', {
				url : '/app/new',
				templateUrl : 'app-item.html',
				controller : 'TurAppNewCtrl',
				data : {
					pageTitle : 'New App | Viglet Turing',
					saveButton : 'Save'
				}
			}).state('app-edit', {
				url : '/app/:appId',
				templateUrl : 'app-item.html',
				controller : 'TurAppEditCtrl',
				data : {
					pageTitle : 'Edit App | Viglet Turing',
					saveButton : 'Update Settings'
				}
			}).state('app-edit.keys', {
				url : '/keys',
				templateUrl : 'app-item-keys.html',
				controller : 'TurAppEditCtrl',
				data : {
					pageTitle : 'Edit App Keys | Viglet Turing'
				}
			});

		} ]);