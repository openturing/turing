turingSNApp.config([
		'$stateProvider',
		'$urlRouterProvider',
		'$locationProvider',
		'$translateProvider',
		function($stateProvider, $urlRouterProvider, $locationProvider,
				$translateProvider) {
			$translateProvider.useSanitizeValueStrategy('escaped');
			$locationProvider.html5Mode(true);

		/*	$urlRouterProvider.otherwise('/sn');
			$stateProvider.state('home?q&p', {
				url : '/sn',
				data : {
					pageTitle : 'Home | Viglet Turing'
				}
			})*/
		} ]);