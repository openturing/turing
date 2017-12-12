turingSNApp.config([
		'$stateProvider',
		'$urlRouterProvider',
		'$locationProvider',
		'$translateProvider',
		function($stateProvider, $urlRouterProvider, $locationProvider,
				$translateProvider) {
			$translateProvider.useSanitizeValueStrategy('escaped');
			$locationProvider.html5Mode(true);
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
				RESULTS: "results"						
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
				RESULTS: "resultados"						
			});
			
			$translateProvider.fallbackLanguage('en');
			
		/*	$urlRouterProvider.otherwise('/sn/search');
			$stateProvider
					.state('search', {
						url : '/sn/search',
						templateUrl : 'sn/templates/home.html',
						controller : 'TurSNMainCtrl',
						data : {
							pageTitle : 'Home | Viglet Turing'
						}
					})*/
		} ]);
turingSNApp.service('turAPIServerService', [
		'$http',
		'$location',
		'$cookies',
		function($http, $location, $cookies) {
			var turProtocol = $location.protocol();
			var turHostname = $location.host();
			var turPort = $location.port();
			var turAPIContext = "/api";
			var turEmbServer = turProtocol + "://" + turHostname + ":"
					+ turPort + turAPIContext;
			console.log(turEmbServer);

			this.get = function() {

				if ($cookies.get('turAPIServer') != null)
					return $cookies.get('turAPIServer');
				else {
					$http({
						method : 'GET',
						url : turEmbServer
					}).then(function successCallback(response) {
						$cookies.put('turAPIServer', turEmbServer);
					}, function errorCallback(response) {
						$cookies.put('turAPIServer', 'http://localhost:2700' + turAPIContext);

					});
					return turEmbServer;
				}
			}
		} ]);
turingSNApp.factory('vigLocale', [
		'$window',
		function($window) {
			return {
				getLocale : function() {
					var nav = $window.navigator;
					if (angular.isArray(nav.languages)) {
						if (nav.languages.length > 0) {
							return nav.languages[0].split('-').join('_');
						}
					}
					return ((nav.language || nav.browserLanguage
							|| nav.systemLanguage || nav.userLanguage) || '')
							.split('-').join('_');
				}
			}
		} ]);
