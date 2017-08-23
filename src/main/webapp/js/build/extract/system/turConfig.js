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
				SETTINGS_SAVE_CHANGES : "Save Changes",
				INTERNAL_NAME :  "Internal Name"
			});
			$translateProvider.translations('pt', {
				NLP_EDIT : "Editar o NLP",
				NLP_EDIT_SUBTITLE : "Altere as configurações do NLP",
				NAME : "Nome",
				DESCRIPTION : "Descrição",
				VENDORS : "Produtos",
				HOST : "Host",
				PORT : "Porta",
				SETTINGS_SAVE_CHANGES : "Salvar Alterações",
				INTERNAL_NAME :  "Nome Interno"
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
			}).state('ml.instance-new', {
				url : '/instance/new',
				templateUrl : 'templates/ml/ml-instance-new.html',
				controller : 'TurMLInstanceNewCtrl',
				data : {
					pageTitle : 'New Machine Learning Instance | Viglet Turing'
				}
			}).state('ml.instance-edit', {
				url : '/instance/:mlInstanceId',
				templateUrl : 'templates/ml/ml-instance-edit.html',
				controller : 'TurMLInstanceEditCtrl',
				data : {
					pageTitle : 'Edit Machine Learning | Viglet Turing'
				}
			}).state('ml.category-edit', {
				url : '/category/:mlCategoryId',
				templateUrl : 'templates/ml/category/ml-category-edit.html',
				controller : 'TurMLCategoryEditCtrl',
				data : {
					pageTitle : 'Edit Category | Viglet Turing'
				}
			})
			.state('ml.model', {
				url : '/model',
				templateUrl : 'templates/ml/model/ml-model.html',
				controller : 'TurMLModelCtrl',
				data : {
					pageTitle : 'Machine Learning Models | Viglet Turing'
				}
			}).state('ml.data-edit', {
				url : '/data/:mlDataId',
				templateUrl : 'templates/ml/data/ml-data-edit.html',
				controller : 'TurMLDataEditCtrl',
				data : {
					pageTitle : 'Edit Data | Viglet Turing'
				}
			}).state('ml.data-edit.sentence', {
				url : '/sentence',
				templateUrl : 'templates/ml/data/ml-data-sentence.html',
				controller : 'TurMLDataSentenceCtrl',
				data : {
					pageTitle : 'Edit Data | Viglet Turing'
				}
			}).state('ml.datagroup', {
				url : '/datagroup',
				templateUrl : 'templates/ml/data/group/ml-datagroup.html',
				controller : 'TurMLDataGroupCtrl',
				data : {
					pageTitle : 'Machine Learning Data Groups | Viglet Turing'
				}
			}).state('ml.datagroup-new', {
				url : '/datagroup/new',
				templateUrl : 'templates/ml/data/group/ml-datagroup-new.html',
				controller : 'TurMLDataGroupNewCtrl',
				data : {
					pageTitle : 'New Data Group | Viglet Turing'
				}
			}).state('ml.datagroup-edit', {
				url : '/datagroup/:mlDataGroupId',
				templateUrl : 'templates/ml/data/group/ml-datagroup-edit.html',
				controller : 'TurMLDataGroupEditCtrl',
				data : {
					pageTitle : 'Edit Data Group | Viglet Turing'
				}
			}).state('ml.datagroup-edit.category', {
				url : '/category',
				templateUrl : 'templates/ml/data/group/ml-datagroup-category.html',
				controller : 'TurMLDataGroupCategoryCtrl',
				data : {
					pageTitle : 'Data Group Categories | Viglet Turing'
				}
			}).state('ml.datagroup-edit.data', {
				url : '/document',
				templateUrl : 'templates/ml/data/group/ml-datagroup-data.html',
				controller : 'TurMLDataGroupDataCtrl',
				data : {
					pageTitle : 'Data Group Documents | Viglet Turing'
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
			}).state('se.instance-new', {
				url : '/instance/new',
				templateUrl : 'templates/se/se-instance-new.html',
				controller : 'TurSEInstanceNewCtrl',
				data : {
					pageTitle : 'New Search Engine Instance | Viglet Turing'
				}
			}).state('se.instance-edit', {
				url : '/instance/:seInstanceId',
				templateUrl : 'templates/se/se-instance-edit.html',
				controller : 'TurSEInstanceEditCtrl',
				data : {
					pageTitle : 'Edit Search Engine | Viglet Turing'
				}
			}).state('sn', {
				url : '/sn',
				templateUrl : 'templates/sn/sn.html',
				data : {
					pageTitle : 'Semantic Navigation | Viglet Turing'
				}
			}).state('sn.site', {
				url : '/site',
				templateUrl : 'templates/sn/sn-site.html',
				controller : 'TurSNSiteCtrl',
				data : {
					pageTitle : 'Semantic Navigation Sites | Viglet Turing'
				}
			}).state('sn.site-new', {
				url : '/site/new',
				templateUrl : 'templates/sn/sn-site-new.html',
				controller : 'TurSNSiteNewCtrl',
				data : {
					pageTitle : 'New Semantic Navigation Site | Viglet Turing'
				}
			}).state('sn.site-edit', {
				url : '/site/:snSiteId',
				templateUrl : 'templates/sn/sn-site-edit.html',
				controller : 'TurSNSiteEditCtrl',
				data : {
					pageTitle : 'Edit Semantic Navigation Site | Viglet Turing'
				}
			}).state('sn.ad', {
				url : '/ad',
				templateUrl : 'templates/sn/sn-ad.html',
				controller : 'TurSNAdvertisingCtrl',
				data : {
					pageTitle : 'Semantic Navigation Advertising | Viglet Turing'
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
			}).state('nlp.instance-new', {
				url : '/instance/new',
				templateUrl : 'templates/nlp/nlp-instance-new.html',
				controller : 'TurNLPInstanceNewCtrl',
				data : {
					pageTitle : 'New NLP Instance | Viglet Turing'
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
			});

		} ]);