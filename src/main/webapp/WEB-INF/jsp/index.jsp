<!doctype html>

<html lang="en">

<head>
    <meta charset="utf-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <meta name="description" content="A front-end template that helps you build fast, modern mobile web apps.">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, minimum-scale=1.0">
    <title>Viglet Turing</title>

    <!-- Add to homescreen for Chrome on Android -->
    <meta name="mobile-web-app-capable" content="yes">
    <link rel="icon" sizes="192x192" href="images/android-desktop.png">

    <!-- Add to homescreen for Safari on iOS -->
    <meta name="apple-mobile-web-app-capable" content="yes">
    <meta name="apple-mobile-web-app-status-bar-style" content="black">
    <meta name="apple-mobile-web-app-title" content="Material Design Lite">
    <link rel="apple-touch-icon-precomposed" href="images/ios-desktop.png">

    <!-- Tile icon for Win8 (144x144 + tile color) -->
    <meta name="msapplication-TileImage" content="images/touch/ms-touch-icon-144x144-precomposed.png">
    <meta name="msapplication-TileColor" content="#3372DF">

    <link rel="shortcut icon" href="images/favicon.png">

    <!-- SEO: If your mobile URL is different from the desktop URL, add a canonical link to the desktop page https://developers.google.com/webmasters/smartphone-sites/feature-phones -->
    <!--
        <link rel="canonical" href="http://www.example.com/">
        -->

    <link rel="stylesheet" href="https://fonts.googleapis.com/css?family=Roboto:regular,bold,italic,thin,light,bolditalic,black,medium&amp;lang=en">
    <link rel="stylesheet" href="https://fonts.googleapis.com/icon?family=Material+Icons">
    <link rel="stylesheet" href="https://code.getmdl.io/1.1.3/material.cyan-light_blue.min.css">
    <link rel="stylesheet" href="styles.css">
    <style>
        #view-source {
            position: fixed;
            display: block;
            right: 0;
            bottom: 0;
            margin-right: 40px;
            margin-bottom: 40px;
            z-index: 900;
        }
    </style>
</head>

<body>
    <div class="demo-layout mdl-layout mdl-js-layout mdl-layout--fixed-drawer mdl-layout--fixed-header">
        <header class="demo-header mdl-layout__header mdl-color--grey-100 mdl-color-text--grey-600">
            <div class="mdl-layout__header-row">
                <span class="mdl-layout-title">Viglet Turing</span>
                <div class="mdl-layout-spacer"></div>
                <div class="mdl-textfield mdl-js-textfield mdl-textfield--expandable">
                    <label class="mdl-button mdl-js-button mdl-button--icon" for="search">
                            <i class="material-icons">search</i>
                        </label>
                    <div class="mdl-textfield__expandable-holder">
                        <input class="mdl-textfield__input" type="text" id="search">
                        <label class="mdl-textfield__label" for="search">Busque no Viglet...</label>
                    </div>
                </div>
                <button class="mdl-button mdl-js-button mdl-js-ripple-effect mdl-button--icon" id="hdrbtn">
                        <i class="material-icons">more_vert</i>
                    </button>
                <ul class="mdl-menu mdl-js-menu mdl-js-ripple-effect mdl-menu--bottom-right" for="hdrbtn">
                    <li class="mdl-menu__item">Sobre</li>
                    <li class="mdl-menu__item">Contato</li>
                    <li class="mdl-menu__item">Privacidade</li>
                </ul>
            </div>
        </header>
        <div class="demo-drawer mdl-layout__drawer mdl-color--blue-grey-900 mdl-color-text--blue-grey-50">
            <header class="demo-drawer-header">
                <img src="https://graph.facebook.com/10152497154243348/picture?width=200&height=200" class="demo-avatar">
                <div class="demo-avatar-dropdown">
                    <span>alexandre.oliveira@gmail.com</span>
                    <div class="mdl-layout-spacer"></div>
                    <button id="accbtn" class="mdl-button mdl-js-button mdl-js-ripple-effect mdl-button--icon">
                            <i class="material-icons" role="presentation">arrow_drop_down</i>
                            <span class="visuallyhidden">Contas</span>
                        </button>
                    <ul class="mdl-menu mdl-menu--bottom-right mdl-js-menu mdl-js-ripple-effect" for="accbtn">
                        <li class="mdl-menu__item">alexandre.oliveira@gmail.com</li>
                        <li class="mdl-menu__item">alegauss@hotmail.com</li>
                        <li class="mdl-menu__item"><i class="material-icons">add</i>Adicione outras contas...</li>
                    </ul>
                </div>
            </header>
            <nav class="demo-navigation mdl-navigation mdl-color--blue-grey-800">
                <a class="mdl-navigation__link" href=""><i class="mdl-color-text--blue-grey-400 material-icons" role="presentation">home</i>Home</a>
                <a class="mdl-navigation__link" href=""><i class="mdl-color-text--blue-grey-400 material-icons" role="presentation">gesture</i>Machine Learning</a>
                <a class="mdl-navigation__link" href=""><i class="mdl-color-text--blue-grey-400 material-icons" role="presentation">text_format</i>NLP</a>
                <a class="mdl-navigation__link" href=""><i class="mdl-color-text--blue-grey-400 material-icons" role="presentation">crop_portrait</i>Database</a>
                <a class="mdl-navigation__link" href=""><i class="mdl-color-text--blue-grey-400 material-icons" role="presentation">find_in_page</i>Search Engine</a>
                <a class="mdl-navigation__link" href=""><i class="mdl-color-text--blue-grey-400 material-icons" role="presentation">supervisor_account</i>Users</a>
                <a class="mdl-navigation__link" href=""><i class="mdl-color-text--blue-grey-400 material-icons" role="presentation">local_offer</i>Content</a>
                <div class="mdl-layout-spacer"></div>
                <a class="mdl-navigation__link" href=""><i class="mdl-color-text--blue-grey-400 material-icons" role="presentation">help_outline</i><span class="visuallyhidden">Help</span></a>
            </nav>
        </div>
        <main class="mdl-layout__content mdl-color--grey-100">
            <div class="mdl-grid demo-content">
                <div class="demo-cards mdl-cell mdl-cell--12-col mdl-cell--12-col-tablet mdl-grid ">
                    <div class="demo-nlp mdl-card mdl-shadow--2dp mdl-cell mdl-cell--4-col mdl-cell--4-col-tablet mdl-cell--3-col-desktop">
                        <div class="mdl-card__title mdl-card--expand mdl-color--teal-300">
                            <h2 class="mdl-card__title-text">NLP</h2>
                        </div>
                        <div class="mdl-card__supporting-text mdl-color-text--grey-600">
                            Está utilizando o CoreNLP.
                        </div>
                        <div class="mdl-card__actions mdl-card--border">
                            <a href="#" class="mdl-button mdl-js-button mdl-js-ripple-effect">Configurações</a>
                        </div>
                    </div>

                    <div class="demo-machine-learning mdl-card mdl-shadow--2dp mdl-cell mdl-cell--4-col mdl-cell--4-col-tablet mdl-cell--3-col-desktop">
                        <div class="mdl-card__title mdl-card--expand mdl-color--teal-300">
                            <h2 class="mdl-card__title-text">Machine Learning</h2>
                        </div>
                        <div class="mdl-card__supporting-text mdl-color-text--grey-600">
                            Está utilizando o TensorFlow.
                        </div>
                        <div class="mdl-card__actions mdl-card--border">
                            <a href="#" class="mdl-button mdl-js-button mdl-js-ripple-effect">Configurações</a>
                        </div>
                    </div>
                    <div class="demo-search-engine mdl-card mdl-shadow--2dp mdl-cell mdl-cell--4-col mdl-cell--4-col-tablet mdl-cell--3-col-desktop">
                        <div class="mdl-card__title mdl-card--expand mdl-color--teal-300">
                            <h2 class="mdl-card__title-text">Search Engine</h2>
                        </div>
                        <div class="mdl-card__supporting-text mdl-color-text--grey-600">
                            Está utilizando o Solr.
                        </div>
                        <div class="mdl-card__actions mdl-card--border">
                            <a href="#" class="mdl-button mdl-js-button mdl-js-ripple-effect">Configurações</a>
                        </div>
                    </div>
                    <div class="demo-database mdl-card mdl-shadow--2dp mdl-cell mdl-cell--4-col mdl-cell--4-col-tablet mdl-cell--3-col-desktop">
                        <div class="mdl-card__title mdl-card--expand mdl-color--teal-300">
                            <h2 class="mdl-card__title-text">Database</h2>
                        </div>
                        <div class="mdl-card__supporting-text mdl-color-text--grey-600">
                            Está utilizando o MySQL.
                        </div>
                        <div class="mdl-card__actions mdl-card--border">
                            <a href="#" class="mdl-button mdl-js-button mdl-js-ripple-effect">Configurações</a>
                        </div>
                    </div>
                </div>


                <div class="demo-cards mdl-cell mdl-cell--12-col mdl-cell--12-col-tablet mdl-grid ">
                    <div class="demo-entity mdl-card mdl-shadow--2dp mdl-cell mdl-cell--4-col mdl-cell--4-col-tablet mdl-cell--3-col-desktop">
                        <div class="mdl-card__title mdl-card--expand mdl-color--light-blue-300">
                            <h2 class="mdl-card__title-text">Entity</h2>
                        </div>
                        <div class="mdl-card__supporting-text mdl-color-text--grey-600">
                            Cadastre Entidades para serem usadas no NLP.
                        </div>
                        <div class="mdl-card__actions mdl-card--border">
                            <a href="#" class="mdl-button mdl-js-button mdl-js-ripple-effect">Editar</a>
                        </div>
                    </div>

                    <div class="demo-models mdl-card mdl-shadow--2dp mdl-cell mdl-cell--4-col mdl-cell--4-col-tablet mdl-cell--3-col-desktop">
                        <div class="mdl-card__title mdl-card--expand mdl-color--light-blue-300">
                            <h2 class="mdl-card__title-text">Models</h2>
                        </div>
                        <div class="mdl-card__supporting-text mdl-color-text--grey-600">
                            Ajude-nos a criar melhores modelos.
                        </div>
                        <div class="mdl-card__actions mdl-card--border">
                            <a href="#" class="mdl-button mdl-js-button mdl-js-ripple-effect">Modele</a>
                        </div>
                    </div>
                    <div class="demo-training mdl-card mdl-shadow--2dp mdl-cell mdl-cell--4-col mdl-cell--4-col-tablet mdl-cell--3-col-desktop">
                        <div class="mdl-card__title mdl-card--expand mdl-color--light-blue-300">
                            <h2 class="mdl-card__title-text">Training</h2>
                        </div>
                        <div class="mdl-card__supporting-text mdl-color-text--grey-600">
                            Treine o NLP ou o Machine Learning.
                        </div>
                        <div class="mdl-card__actions mdl-card--border">
                            <a href="#" class="mdl-button mdl-js-button mdl-js-ripple-effect">Treine</a>
                        </div>
                    </div>
                    <div class="demo-rules mdl-card mdl-shadow--2dp mdl-cell mdl-cell--4-col mdl-cell--4-col-tablet mdl-cell--3-col-desktop">
                        <div class="mdl-card__title mdl-card--expand mdl-color--light-blue-300">
                            <h2 class="mdl-card__title-text">Rules</h2>
                        </div>
                        <div class="mdl-card__supporting-text mdl-color-text--grey-600">
                            Crie Regras para o NLP ou o Machine Learning.
                        </div>
                        <div class="mdl-card__actions mdl-card--border">
                            <a href="#" class="mdl-button mdl-js-button mdl-js-ripple-effect">Crie</a>
                        </div>
                    </div>
                </div>

                <div class="demo-cards mdl-cell mdl-cell--12-col mdl-cell--12-col-tablet mdl-grid ">
                    <div class="demo-indexing mdl-card mdl-shadow--2dp mdl-cell mdl-cell--4-col mdl-cell--4-col-tablet mdl-cell--3-col-desktop">
                        <div class="mdl-card__title mdl-card--expand mdl-color--lime-300">
                            <h2 class="mdl-card__title-text">Indexing</h2>
                        </div>
                        <div class="mdl-card__supporting-text mdl-color-text--grey-600">
                            Indexe os seus conteúdos usando o NLP.
                        </div>
                        <div class="mdl-card__actions mdl-card--border">
                            <a href="#" class="mdl-button mdl-js-button mdl-js-ripple-effect">Integre</a>
                        </div>
                    </div>

                    <div class="demo-oauth2 mdl-card mdl-shadow--2dp mdl-cell mdl-cell--4-col mdl-cell--4-col-tablet mdl-cell--3-col-desktop">
                        <div class="mdl-card__title mdl-card--expand mdl-color--lime-300">
                            <h2 class="mdl-card__title-text">OAuth2</h2>
                        </div>
                        <div class="mdl-card__supporting-text mdl-color-text--grey-600">
                            Permita que outras aplicações utilizem os nossos recursos.
                        </div>
                        <div class="mdl-card__actions mdl-card--border">
                            <a href="#" class="mdl-button mdl-js-button mdl-js-ripple-effect">Registre</a>
                        </div>
                    </div>
                    <div class="demo-plugins mdl-card mdl-shadow--2dp mdl-cell mdl-cell--4-col mdl-cell--4-col-tablet mdl-cell--3-col-desktop">
                        <div class="mdl-card__title mdl-card--expand mdl-color--lime-300">
                            <h2 class="mdl-card__title-text">Plugins</h2>
                        </div>
                        <div class="mdl-card__supporting-text mdl-color-text--grey-600">
                            Adicione novas funcionalidades à Plataforma.
                        </div>
                        <div class="mdl-card__actions mdl-card--border">
                            <a href="#" class="mdl-button mdl-js-button mdl-js-ripple-effect">Configure</a>
                        </div>
                    </div>
                    <div class="demo-users mdl-card mdl-shadow--2dp mdl-cell mdl-cell--4-col mdl-cell--4-col-tablet mdl-cell--3-col-desktop">
                        <div class="mdl-card__title mdl-card--expand mdl-color--lime-300">
                            <h2 class="mdl-card__title-text">Users</h2>
                        </div>
                        <div class="mdl-card__supporting-text mdl-color-text--grey-600">
                            Usuários e permissões na Plataforma.
                        </div>
                        <div class="mdl-card__actions mdl-card--border">
                            <a href="#" class="mdl-button mdl-js-button mdl-js-ripple-effect">Edite</a>
                        </div>
                    </div>
                </div>

            </div>
        </main>
    </div>
    <script src="https://code.getmdl.io/1.1.3/material.min.js"></script>
</body>

</html>