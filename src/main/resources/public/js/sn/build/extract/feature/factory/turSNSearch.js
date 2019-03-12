turingSNApp.factory('turSNSearch', [
		'$http',
		'turAPIServerService',
		function($http, turAPIServerService) {

			return {
				search : function(turSiteName, query, page, _setlocale, sort, fq, tr) {
					var data = {
						'q' : query,
						'p' : page,
						'_setlocale' : _setlocale,
						'sort' : sort,
						'fq[]' : fq,
						'tr[]' : tr
					};
					var config = {
						params : data,
						headers : {
							'Accept' : 'application/json'
						}
					};

					return $http.get(turAPIServerService.get().concat(
							'/sn/' + turSiteName + '/search'), config);
				},
				searchURL : function(url) {
					urlFormatted = url.replace("/api/",
							"/");
					var config = {
						headers : {
							'Accept' : 'application/json'
						}
					};

					return $http.get(turAPIServerService.get().concat(
							urlFormatted), config);
				}
			}
		} ]);