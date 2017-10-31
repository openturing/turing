turingSNApp.factory('turSNSearch', [
		'$http',
		'turAPIServerService',
		function($http, turAPIServerService) {

			return {
				search : function(query, page) {
					var data = {
						'q' : query,
						'sort' : 'relevant',
						'_setlocale' : 'pt',
						'p' : page
					};
					var config = {
						params : data,
						headers : {
							'Accept' : 'application/json'
						}
					};

					return $http.get(turAPIServerService.get().concat(
							'/otsn/search/theme/json'), config);
				}
			}
		} ]);