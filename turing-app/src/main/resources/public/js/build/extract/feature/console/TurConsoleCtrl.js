turingApp.controller('TurConsoleCtrl', [
    "$scope",
    "$http",
    "$rootScope",
    "turAPIServerService",
    "$window",
    "$translate",
    "vigLocale",
    function ($scope, $http, $rootScope, turAPIServerService, $window, $translate, vigLocale) {
        $scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
        $translate.use($scope.vigLanguage);

        //$rootScope.principal = null;
        var authenticate = function (credentials) {

            var headers = credentials ? {
                authorization: "Basic "
                    + btoa(credentials.username + ":"
                        + credentials.password)
            } : {};
        }

        // authenticate();
        if (!$rootScope.authenticated) {
            $http.get(turAPIServerService.get().concat("/v2/user/current"))
                .then(function (response) {
                    if (response.data.username) {
                        $rootScope.principal = response.data;
                        $rootScope.authenticated = true;
                    } else {
                        $rootScope.authenticated = false;
                        $window.location.href = "/";
                    }

                }, function () {
                    $rootScope.authenticated = false;
                    $window.location.href = "/";
                });
        }
        $scope.credentials = {};
        $scope.login = function () {
            authenticate($scope.credentials);
        };

        $rootScope.logout = function () {
            $http.post('logout', {}).then(function () {
                $rootScope.authenticated = false;
                $window.location.href = "/";
            }, function (data) {
                $rootScope.authenticated = false;
            });
        }
    }]);
