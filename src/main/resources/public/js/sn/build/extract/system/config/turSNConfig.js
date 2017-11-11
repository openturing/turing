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

				REMOVE : "Remove"
			});
			$translateProvider.translations('pt', {
				REMOVE : "Remover"
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