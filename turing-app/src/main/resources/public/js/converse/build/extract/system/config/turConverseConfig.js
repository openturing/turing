turConverseApp.config([
		'$stateProvider',
		'$urlRouterProvider',
		'$locationProvider',
		'$translateProvider',
		function($stateProvider, $urlRouterProvider, $locationProvider,
				$translateProvider) {
			$translateProvider.useSanitizeValueStrategy('escaped');
			$translateProvider.translations('en', {
				REMOVE : "Remove",
				FIRST: "First",
				LAST: "Last",
				PREVIOUS: "Previous",
				NEXT: "Next",
				SEARCH: "Search",
				SEARCH_FOR: "Search for",
				NO_RESULTS_FOUND:"No results found",
				APPLIED_FILTERS: "Applied Filters",
				SHOWING:  "Showing",
				OF: "of",
				RESULTS: "results",
				ORDER_BY: "Order by",
				RELEVANCE: "Relevance",
				NEWEST: "Newest",
				OLDEST: "Oldest",
				SUBJECTS_FOUND: "Subjects found"
			});
			$translateProvider.translations('pt', {
				REMOVE : "Remover",
				FIRST: "Primeiro",
				LAST: "Último",
				PREVIOUS: "Anterior",
				NEXT: "Próximo",
				SEARCH: "Pesquisar",
				SEARCH_FOR: "Pesquisar por",
				NO_RESULTS_FOUND: "Nenhum resultado encontrado",
				APPLIED_FILTERS: "Filtros Aplicados",
				SHOWING:  "Exibindo",
				OF: "de",
				RESULTS: "resultados",
				ORDER_BY: "Ordenar por",
				RELEVANCE: "Relevância",
				NEWEST: "Mais recente",
				OLDEST: "Mais antigo",
				SUBJECTS_FOUND: "Assuntos Encontrados"

			});
			
			$translateProvider.fallbackLanguage('en');	
			$urlRouterProvider.otherwise('');
			$stateProvider
				.state('home', {
					url : '',
					templateUrl: '/converse/template/converse.html',		
					controller: 'TurConverseMainCtrl',		
					data: {
						pageTitle: 'Converse | Viglet Turing'
					}
				})
				.state(
					'agent',
					{
						url: '/:agentId',
						templateUrl: '/converse/template/converse.html',
						controller: 'TurConverseMainCtrl',
						data: {
							pageTitle: 'Converse | Viglet Turing'
						}
					})
		} ]);