turingApp.controller('TurMLDataSentenceCtrl', [
		"$scope",
		"$stateParams",
		"$state",
		"$rootScope",
		"$translate",
		"vigLocale",
		"$uibModal",
		"turMLCategoryResource",
		function($scope, $stateParams, $state, $rootScope, $translate,
				vigLocale, $uibModal, turMLCategoryResource) {

			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);
			$rootScope.$state = $state;
			$scope.categories = turMLCategoryResource.query();
		} ]);