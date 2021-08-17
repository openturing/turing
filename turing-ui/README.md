[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=openturing_turing&metric=alert_status)](https://sonarcloud.io/dashboard?id=openturing_turing) [![Twitter](https://img.shields.io/twitter/follow/openturing.svg?style=social&label=Follow)](https://twitter.com/intent/follow?screen_name=openturing)


**If you'd like to contribute to Turing AI UI Primer, be sure to review the [contribution
guidelines](CONTRIBUTING.md).**

**We use [GitHub issues](https://github.com/openturing/turing/issues) for tracking requests and bugs.**

# Installation

## Download

```shell
$ cd turing-ui
```

## Deploy 

### 1. Install NPM Modules

Use NPM to install the modules.

```shell
$ npm install
```

### 2. Start Turing AI

Start Turing AI using the ui-dev profile.

#### Linux

```shell
export SPRING_PROFILES_ACTIVE=ui-dev
$ cd ..
$ ./gradlew turing-app:bootrun
```
#### Windows

```shell
set SPRING_PROFILES_ACTIVE=ui-dev
gradlew turing-app:bootrun
```


### 3. Runtime

Use ng to execute Turing AI UI.

```shell
$ npx ng serve --open
```

### 4. Build

Use ng to build Turing AI UI.

```shell
$ npx ng build --prod
```

## URL

Home: http://localhost:4200
