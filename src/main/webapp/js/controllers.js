var vigletApp = angular.module('vigletApp', [ "ngRoute" ]);

vigletApp.config([ '$routeProvider', function($routeProvider) {
	$routeProvider.when('/home', {
		templateUrl : 'templates/home.html',
		controller : 'AddOrderController'
	}).when('/entity', {
		templateUrl : 'templates/entity.html',
		controller : 'ShowOrdersController'
	}).when('/entity/import', {
		templateUrl : 'templates/entity/entity_import.html',
		controller : 'ShowOrdersController'
	}).when('/entity/:id', {
		templateUrl : 'templates/entity_item.html',
		controller : 'ShowOrdersController'
	}).when('/nlp', {
		templateUrl : 'templates/nlp.html',
		controller : 'ShowOrdersController'
	}).when('/nlp/:id', {
		templateUrl : 'templates/nlp_item.html',
		controller : 'ShowOrdersController'
	}).when('/ml', {
		templateUrl : 'templates/ml.html',
		controller : 'ShowOrdersController'
	}).when('/search-engine', {
		templateUrl : 'templates/search-engine.html',
		controller : 'ShowOrdersController'
	}).when('/database', {
		templateUrl : 'templates/database.html',
		controller : 'ShowOrdersController'
	}).when('/indexing', {
		templateUrl : 'templates/indexing.html',
		controller : 'IndexingCtrl'
	}).when('/indexingDocument', {
		templateUrl : 'templates/indexingResult.html',
		controller : 'IndexingCtrl'
	}).when('/model', {
		templateUrl : 'templates/model/model.html',
		controller : 'MLModelCtrl'
	}).when('/data/group', {
		templateUrl : 'templates/data/group/datagroup.html',
		controller : 'MLDataGroupCtrl'
	}).when('/data/group/create', {
		templateUrl : 'templates/data/group/datagroup_create.html',
		controller : 'MLDataGroupItemCtrl'
	}).when('/data/group/:id', {
		templateUrl : 'templates/data/group/datagroup_item.html',
		controller : 'MLDataGroupItemCtrl'
	}).when('/data/import', {
		templateUrl : 'templates/data/data_import.html',
		controller : 'MLDataGroupCtrl'
	}).when('/data/:id', {
		templateUrl : 'templates/data/data_item.html',
		controller : 'MLDataItemCtrl'
	}).otherwise({
		redirectTo : '/home'
	});
} ]);

vigletApp.controller('IndexingCtrl', function($scope, $http, $routeParams,
		$location) {
	$scope.vigResults = null;
	$scope.isArray = angular.isArray;
	$scope.vigText = null;
	$scope.nlpmodel = null;
	$scope.semodel = null;
	$http.get("/turing/api/nlp/").success(function(data) {
		$scope.vigNLPServices = data;
		angular.forEach(data, function(value, key) {
			if (value.enabled == true) {
				$scope.nlpmodel = value.id;
			}
		});
	});

	$http.get("/turing/api/se/").success(function(data) {
		$scope.vigSEServices = data;
		angular.forEach(data, function(value, key) {
			if (value.enabled == true) {

				$scope.semodel = value.id;
			}
		});
	});
	componentHandler.upgradeAllRegistered();

	$scope.changeView = function(view) {
		$scope.vigResults = null;
		postData = 'vigText=' + $scope.vigText + "&vigNLP=" + $scope.nlpmodel
				+ "&vigSE=" + $scope.semodel;
		$http({
			method : 'POST',
			url : '/turing/api/se/update',
			data : postData, // forms user object
			headers : {
				'Content-Type' : 'application/x-www-form-urlencoded'
			}
		}).success(function(data, status, headers, config) {
			$scope.vigResults = data;

		});

	};

	componentHandler.upgradeAllRegistered();

});

vigletApp.controller('AddOrderController', function($scope) {

	$scope.message = 'This is Add new order screen';
	componentHandler.upgradeAllRegistered();

});

vigletApp.controller('ShowOrdersController', function($scope) {

	$scope.message = 'This is Show orders screen';
	componentHandler.upgradeAllRegistered();

});
vigletApp.controller('MLModelCtrl', function($scope, $http) {
	$scope.vigMLModels = null;
	$http.get("/turing/api/ml/model").then(function(response) {
		$scope.vigMLModels = response.data;
	});
	componentHandler.upgradeAllRegistered();

});

vigletApp.controller('MLDataItemCtrl', function($scope, $http, $routeParams) {
	$scope.vigData = null;
	$http.get("/turing/api/ml/data/" + $routeParams.id).success(function(data) {
		$scope.vigData = data;
	});
	componentHandler.upgradeAllRegistered();

});

vigletApp.controller('MLDataGroupCtrl', function($scope, $http) {
	$scope.vigDataGroups = null;
	$http.get("/turing/api/ml/data/group").then(function(response) {
		$scope.vigDataGroups = response.data;
	});
	componentHandler.upgradeAllRegistered();

});

vigletApp.controller('MLDataGroupItemCtrl', function($scope, $http,
		$routeParams) {
	$scope.vigDataGroup = null;
	if ($routeParams.id != null) {
		$http.get("/turing/api/ml/data/group/" + $routeParams.id).success(
				function(data) {
					$scope.vigDataGroup = data;
				});
	}
	$scope.create = function($dataGroup, $window) {

		postData = 'name=' + $dataGroup.name + "&description="
				+ $dataGroup.description;
		$http({
			method : 'POST',
			url : '/turing/api/ml/data/group/create',
			data : postData, // forms user object
			headers : {
				'Content-Type' : 'application/x-www-form-urlencoded'
			}
		}).success(function(data, status, headers, config) {
			$scope.vigResults = data;

		});
		
		// $window.location.href = '/turing/#/data/group';
		return $scope.vigResults;
	}

	componentHandler.upgradeAllRegistered();

});

vigletApp.controller('EntityServicesCtrl', function($scope, $http) {
	$scope.vigEntityServices = null;
	$http.get("/turing/api/entity").then(function(response) {
		console.log(response.data)
		$scope.vigEntityServices = response.data;
	});
	componentHandler.upgradeAllRegistered();

});
vigletApp.controller('EntityCtrl', function($scope, $http, $routeParams) {

	$http.get("/turing/api/entity/" + $routeParams.id).success(function(data) {
		$scope.vigEntity = data;
	});
	componentHandler.upgradeAllRegistered();

});
vigletApp.controller('NLPServicesCtrl', function($scope, $http) {
	$scope.vigNLPServices = null;
	$http.get("/turing/api/nlp").then(function(response) {
		console.log(response.data)
		$scope.vigNLPServices = response.data;
	});
	componentHandler.upgradeAllRegistered();

});

vigletApp.controller('NLPServiceCtrl', function($scope, $http, $routeParams) {

	$http.get("/turing/api/nlp/" + $routeParams.id).success(function(data) {
		$scope.vigNLPService = data;
	});
	$http.get("/turing/api/nlp/solution").success(function(data) {
		$scope.vigNLPSolutions = data;
	});
	componentHandler.upgradeAllRegistered();

});
