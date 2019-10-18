turingApp
	.config([
		'$stateProvider',
		'$urlRouterProvider',
		'$locationProvider',
		'$translateProvider',
		'NotificationProvider',
		function ($stateProvider, $urlRouterProvider, $locationProvider,
			$translateProvider, NotificationProvider) {
			$translateProvider.useSanitizeValueStrategy('escaped');

			NotificationProvider.setOptions({
				delay : 5000,
				startTop : 20,
				startRight : 10,
				verticalSpacing : 20,
				horizontalSpacing : 20,
				positionX : 'right',
				positionY : 'bottom'
			});
			
			$translateProvider.translations('en', {

				NLP_EDIT: "Edit NLP",
				NLP_EDIT_SUBTITLE: "Change the NLP Settings",
				NAME: "Name",
				DESCRIPTION: "Description",
				VENDORS: "Vendors",
				HOST: "Host",
				PORT: "Port",
				SETTINGS_SAVE_CHANGES: "Save Changes",
				INTERNAL_NAME: "Internal Name",
				SENTENCE: "Sentence",
				SENTENCES: "Sentences",
				CATEGORY: "Category",
				LANGUAGE: "Language",
				CORE: "Core",
				SE: "Search Engine",
				NLP: "NLP",
				TYPE: "Type",
				FACET: "Facet",
				FACETS: "Facets",
				FACET_NAME: "Facet Name",
				MULTI_VALUED: "Multi Valued",
				HIGHLIGHTING: "Highlighting",
				NEW_FIELD: "New Field",
				FIELD: "Field",
				FIELDS: "Fields",
				DETAIL: "Detail",
				APPEARANCE: "Appearance",
				DELETE: "Delete",
				NEW: "New",
				ENABLED: "Enabled",
				SYNC: "Sync",
				THESAURUS: "Thesaurus",
				NER: "NER",
				YES: "Yes",
				NO: "No",
				ITEMS_PER_PAGE: "Number of items per page",
				ITEMS_PER_FACET: "Number of items per facet",
				FACET_ENABLED: "Facet enabled?",
				MLT_ENABLED: "More Like This enabled?",
				HL_ENABLED: "Highlighting enabled?",
				HL_PRE: "Pre Tag",
				HL_POST: "Post Tag",
				REQUIRED: "Required",
				DEFAULT_VALUE: "Default Value",
				NONE: "None",
				TEXT: "Text",
				DATE: "Date",
				URL: "URL",
				DEFAULT_FIELDS: "Default Fields"


			});
			$translateProvider.translations('pt', {
				NLP_EDIT: "Editar o NLP",
				NLP_EDIT_SUBTITLE: "Altere as configurações do NLP",
				NAME: "Nome",
				DESCRIPTION: "Descrição",
				VENDORS: "Produtos",
				HOST: "Host",
				PORT: "Porta",
				SETTINGS_SAVE_CHANGES: "Salvar Alterações",
				INTERNAL_NAME: "Nome Interno",
				SENTENCE: "Sentença",
				SENTENCES: "Sentenças",
				CATEGORY: "Categoria",
				LANGUAGE: "Idioma",
				CORE: "Instância",
				SE: "Motor de Busca",
				NLP: "NLP",
				TYPE: "Tipo",
				FACET: "Faceta",
				FACETS: "Facetas",
				FACET_NAME: "Nome da Faceta",
				MULTI_VALUED: "Múltiplos Valores",
				HIGHLIGHTING: "Realce",
				NEW_FIELD: "Novo Campo",
				FIELD: "Campo",
				FIELDS: "Campos",
				DETAIL: "Detalhe",
				APPEARANCE: "Aparência",
				DELETE: "Apagar",
				NEW: "Novo",
				ENABLED: "Ativado",
				SYNC: "Sincronizar",
				THESAURUS: "Thesaurus",
				NER: "NER",
				YES: "Sim",
				NO: "Não",
				ITEMS_PER_PAGE: "Número de itens por página",
				ITEMS_PER_FACET: "Número de itens por faceta",
				FACET_ENABLED: "Faceta ativada?",
				MLT_ENABLED: "Conteúdos Similares ativados?",
				HL_ENABLED: "Realce ativado?",
				HL_PRE: "Tag Pré",
				HL_POST: "Tag Pós",
				REQUIRED: "Requerido",
				DEFAULT_VALUE: "Valor Padrão",
				NONE: "Nenhum",
				TEXT: "Texto",
				DATE: "Data",
				URL: "URL",
				DEFAULT_FIELDS: "Campos Padrões"


			});
			$translateProvider.fallbackLanguage('en');

			$urlRouterProvider.otherwise('/home');
			$stateProvider
				.state('home', {
					url: '/home',
					templateUrl: 'templates/home.html',
					controller: 'TurHomeCtrl',
					data: {
						pageTitle: 'Home | Viglet Turing'
					}
				})
				.state(
					'ml',
					{
						url: '/ml',
						templateUrl: 'templates/ml/ml.html',
						data: {
							pageTitle: 'Machine Learning | Viglet Turing'
						}
					})
				.state(
					'ml.instance',
					{
						url: '/instance',
						templateUrl: 'templates/ml/ml-instance.html',
						controller: 'TurMLInstanceCtrl',
						data: {
							pageTitle: 'Machine Learnings | Viglet Turing'
						}
					})
				.state(
					'ml.instance-new',
					{
						url: '/instance/new',
						templateUrl: 'templates/ml/ml-instance-new.html',
						controller: 'TurMLInstanceNewCtrl',
						data: {
							pageTitle: 'New Machine Learning Instance | Viglet Turing'
						}
					})
				.state(
					'ml.instance-edit',
					{
						url: '/instance/:mlInstanceId',
						templateUrl: 'templates/ml/ml-instance-edit.html',
						controller: 'TurMLInstanceEditCtrl',
						data: {
							pageTitle: 'Edit Machine Learning | Viglet Turing'
						}
					})
				.state(
					'ml.model',
					{
						url: '/model',
						templateUrl: 'templates/ml/model/ml-model.html',
						controller: 'TurMLModelCtrl',
						data: {
							pageTitle: 'Machine Learning Models | Viglet Turing'
						}
					})
				.state(
					'ml.datagroup',
					{
						url: '/datagroup',
						templateUrl: 'templates/ml/data/group/ml-datagroup.html',
						controller: 'TurMLDataGroupCtrl',
						data: {
							pageTitle: 'Machine Learning Data Groups | Viglet Turing'
						}
					})
				.state(
					'ml.datagroup-new',
					{
						url: '/datagroup/new',
						templateUrl: 'templates/ml/data/group/ml-datagroup-new.html',
						controller: 'TurMLDataGroupNewCtrl',
						data: {
							pageTitle: 'New Data Group | Viglet Turing'
						}
					})
				.state(
					'ml.datagroup-edit',
					{
						url: '/datagroup/:mlDataGroupId',
						templateUrl: 'templates/ml/data/group/ml-datagroup-edit.html',
						controller: 'TurMLDataGroupEditCtrl',
						data: {
							pageTitle: 'Edit Data Group | Viglet Turing'
						}
					})
				.state(
					'ml.datagroup-edit.category',
					{
						url: '/category',
						templateUrl: 'templates/ml/data/group/ml-datagroup-category.html',
						controller: 'TurMLDataGroupCategoryCtrl',
						data: {
							pageTitle: 'Data Group Categories | Viglet Turing'
						}
					})
				.state(
					'ml.datagroup-edit.category-edit',
					{
						url: '/category/:mlCategoryId',
						templateUrl: 'templates/ml/category/ml-category-edit.html',
						controller: 'TurMLCategoryEditCtrl',
						data: {
							pageTitle: 'Edit Category | Viglet Turing'
						}
					})
				.state(
					'ml.datagroup-edit.category-edit.sentence',
					{
						url: '/sentence',
						templateUrl: 'templates/ml/category/ml-category-sentence.html',
						controller: 'TurMLCategorySentenceCtrl',
						data: {
							pageTitle: 'Edit Category | Viglet Turing'
						}
					})
				.state(
					'ml.datagroup-edit.data',
					{
						url: '/data',
						templateUrl: 'templates/ml/data/group/ml-datagroup-data.html',
						controller: 'TurMLDataGroupDataCtrl',
						data: {
							pageTitle: 'Data Group Documents | Viglet Turing'
						}
					})
				.state(
					'ml.datagroup-edit.data-edit',
					{
						url: '/data/:mlDataId',
						templateUrl: 'templates/ml/data/ml-data-edit.html',
						controller: 'TurMLDataEditCtrl',
						data: {
							pageTitle: 'Edit Data | Viglet Turing'
						}
					})
				.state(
					'ml.datagroup-edit.sentence',
					{
						url: '/sentence',
						templateUrl: 'templates/ml/data/group/ml-datagroup-sentence.html',
						controller: 'TurMLDataGroupSentenceCtrl',
						data: {
							pageTitle: 'Data Group Sentences | Viglet Turing'
						}
					})
				.state(
					'ml.datagroup-edit.sentence-edit',
					{
						url: '/sentence/:mlSentenceId',
						templateUrl: 'templates/ml/sentence/ml-sentence-edit.html',
						controller: 'TurMLDataGroupSentenceEditCtrl',
						data: {
							pageTitle: 'Edit Sentence | Viglet Turing'
						}
					})
				.state(
					'ml.datagroup-edit.model',
					{
						url: '/model',
						templateUrl: 'templates/ml/data/group/ml-datagroup-model.html',
						controller: 'TurMLDataGroupModelCtrl',
						data: {
							pageTitle: 'Data Group Models | Viglet Turing'
						}
					})
				.state(
					'ml.datagroup-edit.model-edit',
					{
						url: '/model/:mlModelId',
						templateUrl: 'templates/ml/model/ml-model-edit.html',
						controller: 'TurMLDataGroupModelEditCtrl',
						data: {
							pageTitle: 'Edit Model | Viglet Turing'
						}
					})
				.state(
					'ml.datagroup-edit.data-edit.sentence',
					{
						url: '/sentence',
						templateUrl: 'templates/ml/data/ml-data-sentence.html',
						controller: 'TurMLDataSentenceCtrl',
						data: {
							pageTitle: 'Edit Data | Viglet Turing'
						}
					})
				.state(
					'converse',
					{
						url: '/converse',
						templateUrl: 'templates/converse/converse.html',
						data: {
							pageTitle: 'Converse | Viglet Turing'
						}
					})
				.state(
					'converse.agent-list',
					{
						url: '/agent',
						templateUrl: 'templates/converse/converse-agent-list.html',
						controller: 'TurConverseAgentListCtrl',
						data: {
							pageTitle: 'Converse Agents | Viglet Turing'
						}
					})
				.state(
					'converse.agent-import',
					{
						url: '/agent/import',
						templateUrl: 'templates/converse/converse-agent-import.html',
						controller: 'TurConverseAgentImportCtrl',
						data: {
							pageTitle: 'Import Agent | Viglet Turing'
						}
					})
				.state(
					'converse.agent-new',
					{
						url: '/agent/new',
						templateUrl: 'templates/converse/converse-agent-new.html',
						controller: 'TurConverseAgentDetailCtrl',
						data: {
							pageTitle: 'New Converse Agent | Viglet Turing'
						}
					})
				.state(
					'converse.agent',
					{
						url: '/agent/:agentId',
						templateUrl: 'templates/converse/converse-agent.html',
						controller: 'TurConverseAgentCtrl',
						data: {
							pageTitle: 'Edit Converse Agent | Viglet Turing'
						}
					})
				.state(
					'converse.agent.detail',
					{
						url: '/detail',
						templateUrl: 'templates/converse/converse-agent-detail.html',
						controller: 'TurConverseAgentDetailCtrl',
						data: {
							pageTitle: 'Converse Agent Detail | Viglet Turing'
						}
					})
				.state(
					'converse.agent.intent',
					{
						url: '/intent',
						templateUrl: 'templates/converse/converse-intent-list.html',
						controller: 'TurConverseIntentListCtrl',
						data: {
							pageTitle: 'Converse Intents | Viglet Turing'
						}
					})
				.state(
					'converse.agent.intent-new',
					{
						url: '/intent/new',
						templateUrl: 'templates/converse/converse-intent.html',
						controller: 'TurConverseIntentCtrl',
						data: {
							pageTitle: 'New Converse Intent | Viglet Turing'
						}
					})
				.state(
					'converse.agent.intent-edit',
					{
						url: '/intent/:intentId',
						templateUrl: 'templates/converse/converse-intent.html',
						controller: 'TurConverseIntentCtrl',
						data: {
							pageTitle: 'Edit Converse Intent | Viglet Turing'
						}
					})
				.state(
					'converse.agent.entity',
					{
						url: '/entity',
						templateUrl: 'templates/converse/converse-entity-list.html',
						controller: 'TurConverseEntityListCtrl',
						data: {
							pageTitle: 'Converse Entity | Viglet Turing'
						}
					})
				.state(
					'converse.agent.entity-new',
					{
						url: '/entity/new',
						templateUrl: 'templates/converse/converse-entity.html',
						controller: 'TurConverseEntityCtrl',
						data: {
							pageTitle: 'New Converse Entity | Viglet Turing'
						}
					})
				.state(
					'converse.agent.entity-edit',
					{
						url: '/entity/:entityId',
						templateUrl: 'templates/converse/converse-entity.html',
						controller: 'TurConverseEntityCtrl',
						data: {
							pageTitle: 'Edit Converse Entity | Viglet Turing'
						}
					})
				.state(
					'converse.agent.training-list',
					{
						url: '/training',
						templateUrl: 'templates/converse/converse-training-list.html',
						controller: 'TurConverseTrainingListCtrl',
						data: {
							pageTitle: 'Converse Training | Viglet Turing'
						}
					})
				.state(
					'converse.agent.training',
					{
						url: '/training/:conversationId',
						templateUrl: 'templates/converse/converse-training.html',
						controller: 'TurConverseTrainingCtrl',
						data: {
							pageTitle: 'Converse Training | Viglet Turing'
						}
					})
				.state(
					'converse.agent.history',
					{
						url: '/history',
						templateUrl: 'templates/converse/converse-history.html',
						controller: 'TurConverseHistoryCtrl',
						data: {
							pageTitle: 'Converse History | Viglet Turing'
						}
					})
				.state(
					'converse.agent.history-edit',
					{
						url: '/history/:conversationId',
						templateUrl: 'templates/converse/converse-conversation.html',
						controller: 'TurConverseConversationCtrl',
						data: {
							pageTitle: 'Converse Conversation | Viglet Turing'
						}
					})
				.state(
					'converse.agent.prebuilt-agent',
					{
						url: '/prebuiltagent',
						templateUrl: 'templates/converse/converse-prebuilt-agent.html',
						controller: 'TurConversePreBuiltAgentCtrl',
						data: {
							pageTitle: 'Converse Prebuilt Agents | Viglet Turing'
						}
					})
				.state('storage', {
					url: '/storage',
					templateUrl: 'templates/storage/storage.html',
					data: {
						pageTitle: 'Storage | Viglet Turing'
					}
				})
				.state(
					'storage.instance',
					{
						url: '/instance',
						templateUrl: 'templates/storage/storage-instance.html',
						controller: 'TurStorageInstanceCtrl',
						data: {
							pageTitle: 'Storages | Viglet Turing'
						}
					})
				.state(
					'storage.mgmt',
					{
						url: '/mgmt',
						templateUrl: 'templates/storage/mgmt/storage-mgmt.html',
						controller: 'TurStorageMgmtCtrl',
						data: {
							pageTitle: 'Storages | Viglet Turing'
						}
					})
				.state(
					'storage.mgmt-child',
					{
						url: '/mgmt?path',
						templateUrl: 'templates/storage/mgmt/storage-mgmt-child.html',
						controller: 'TurStorageMgmtCtrl',
						data: {
							pageTitle: 'Storages | Viglet Turing'
						}
					})
				.state('se', {
					url: '/se',
					templateUrl: 'templates/se/se.html',
					data: {
						pageTitle: 'Search Engine | Viglet Turing'
					}
				})
				.state(
					'se.instance',
					{
						url: '/instance',
						templateUrl: 'templates/se/se-instance.html',
						controller: 'TurSEInstanceCtrl',
						data: {
							pageTitle: 'Search Engines | Viglet Turing'
						}
					})
				.state(
					'se.instance-new',
					{
						url: '/instance/new',
						templateUrl: 'templates/se/se-instance-new.html',
						controller: 'TurSEInstanceNewCtrl',
						data: {
							pageTitle: 'New Search Engine Instance | Viglet Turing'
						}
					})
				.state(
					'se.instance-edit',
					{
						url: '/instance/:seInstanceId',
						templateUrl: 'templates/se/se-instance-edit.html',
						controller: 'TurSEInstanceEditCtrl',
						data: {
							pageTitle: 'Edit Search Engine | Viglet Turing'
						}
					})
				.state(
					'sn',
					{
						url: '/sn',
						templateUrl: 'templates/sn/sn.html',
						data: {
							pageTitle: 'Semantic Navigation | Viglet Turing'
						}
					})
				.state(
					'sn.site',
					{
						url: '/site',
						templateUrl: 'templates/sn/site/sn-site.html',
						controller: 'TurSNSiteCtrl',
						data: {
							pageTitle: 'Semantic Navigation Sites | Viglet Turing'
						}
					})
				.state(
					'sn.site-new',
					{
						url: '/site/new',
						templateUrl: 'templates/sn/site/sn-site-new.html',
						controller: 'TurSNSiteNewCtrl',
						data: {
							pageTitle: 'New Semantic Navigation Site | Viglet Turing'
						}
					})
				.state(
					'sn.site-edit',
					{
						url: '/site/:snSiteId',
						templateUrl: 'templates/sn/site/sn-site-edit.html',
						controller: 'TurSNSiteEditCtrl',
						data: {
							pageTitle: 'Edit Semantic Navigation Site | Viglet Turing'
						}
					})
				.state(
					'sn.site-edit.field',
					{
						url: '/field',
						templateUrl: 'templates/sn/site/sn-site-field.html',
						controller: 'TurSNSiteFieldCtrl',
						data: {
							pageTitle: 'Semantic Navigation Site Fields | Viglet Turing'
						}
					})
				.state(
					'sn.site-edit.field-edit',
					{
						url: '/field/:snSiteFieldId',
						templateUrl: 'templates/sn/site/field/sn-site-field-edit.html',
						controller: 'TurSNSiteFieldEditCtrl',
						data: {
							pageTitle: 'Edit Semantic Navigation Site Field | Viglet Turing'
						}
					})
				.state(
					'sn.site-edit.ui',
					{
						url: '/ui',
						templateUrl: 'templates/sn/site/sn-site-ui.html',
						controller: 'TurSNSiteUICtrl',
						data: {
							pageTitle: 'Semantic Navigation Site Appearance | Viglet Turing'
						}
					})
				.state(
					'sn.ad',
					{
						url: '/ad',
						templateUrl: 'templates/sn/sn-ad.html',
						controller: 'TurSNAdvertisingCtrl',
						data: {
							pageTitle: 'Semantic Navigation Advertising | Viglet Turing'
						}
					})
				.state('nlp', {
					url: '/nlp',
					templateUrl: 'templates/nlp/nlp.html',
					data: {
						pageTitle: 'NLP | Viglet Turing'
					}
				})
				.state(
					'nlp.instance',
					{
						url: '/instance',
						templateUrl: 'templates/nlp/nlp-instance.html',
						controller: 'TurNLPInstanceCtrl',
						data: {
							pageTitle: 'NLPs | Viglet Turing'
						}
					})
				.state(
					'nlp.instance-new',
					{
						url: '/instance/new',
						templateUrl: 'templates/nlp/nlp-instance-new.html',
						controller: 'TurNLPInstanceNewCtrl',
						data: {
							pageTitle: 'New NLP Instance | Viglet Turing'
						}
					})
				.state(
					'nlp.instance-edit',
					{
						url: '/instance/:nlpInstanceId',
						templateUrl: 'templates/nlp/nlp-instance-edit.html',
						controller: 'TurNLPInstanceEditCtrl',
						data: {
							pageTitle: 'Edit NLP | Viglet Turing'
						}
					})
				.state(
					'nlp.validation',
					{
						url: '/validation',
						templateUrl: 'templates/nlp/nlp-validation.html',
						controller: 'TurNLPValidationCtrl',
						data: {
							pageTitle: 'NLP Validation | Viglet Turing'
						}
					})
				.state(
					'nlp.entity',
					{
						url: '/entity',
						templateUrl: 'templates/nlp/entity/nlp-entity.html',
						controller: 'TurNLPEntityCtrl',
						data: {
							pageTitle: 'NLP Entities | Viglet Turing'
						}
					})
				.state(
					'nlp.entity-import',
					{
						url: '/entity/import',
						templateUrl: 'templates/nlp/entity/nlp-entity-import.html',
						data: {
							pageTitle: 'Import Entity | Viglet Turing'
						}
					})
				.state(
					'nlp.entity-edit',
					{
						url: '/entity/:nlpEntityId',
						templateUrl: 'templates/nlp/entity/nlp-entity-edit.html',
						controller: 'TurNLPEntityEditCtrl',
						data: {
							pageTitle: 'Edit Entity | Viglet Turing'
						}
					})
				.state(
					'nlp.entity-edit.term',
					{
						url: '/term',
						templateUrl: 'templates/nlp/entity/nlp-entity-term.html',
						data: {
							pageTitle: 'Entity Terms | Viglet Turing'
						}
					});

		}]);