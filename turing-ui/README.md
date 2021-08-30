
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
