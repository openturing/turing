var turingApp=angular.module("turingApp",["ngResource","ngAnimate","ngSanitize","ui.router","ui.bootstrap","pascalprecht.translate"]);turingApp.directive("convertToNumber",function(){return{require:"ngModel",link:function(c,b,a,d){d.$parsers.push(function(e){return parseInt(e,10)});d.$formatters.push(function(e){return""+e})}}});turingApp.directive("fileModel",["$parse",function(a){return{restrict:"A",link:function(f,e,d){var c=a(d.fileModel);var b=c.assign;e.bind("change",function(){f.$apply(function(){b(f,e[0].files[0])})})}}}]);turingApp.service("fileUpload",["$http",function(a){this.uploadFileToUrl=function(d,b){var c=new FormData();c.append("file",d);a.post(b,c,{transformRequest:angular.identity,headers:{"Content-Type":undefined}})}}]);turingApp.service("turNotificationService",["$http",function(a){this.notifications=[];this.addNotification=function(b){this.notifications.push({msg:b})}}]);turingApp.factory("vigLocale",["$window",function(a){return{getLocale:function(){var b=a.navigator;if(angular.isArray(b.languages)){if(b.languages.length>0){return b.languages[0].split("-").join("_")}}return((b.language||b.browserLanguage||b.systemLanguage||b.userLanguage)||"").split("-").join("_")}}}]);turingApp.controller("TurHomeCtrl",["$scope","$http","$window","$state","$rootScope","$translate",function(b,f,e,c,a,d){b.accesses=null;a.$state=c}]);turingApp.controller("TurMLCategoryEditCtrl",["$scope","$stateParams","$state","$rootScope","$translate","vigLocale","turMLCategoryResource","turNotificationService","$uibModal",function(h,g,a,f,b,i,e,c,d){h.vigLanguage=i.getLocale().substring(0,2);b.use(h.vigLanguage);f.$state=a;h.category=e.get({id:g.mlCategoryId});h.mlCategoryUpdate=function(){h.category.$update(function(){c.addNotification('Category "'+h.category.name+'" was saved.')})};h.mlCategoryDelete=function(){var k=this;var j=d.open({animation:true,ariaLabelledBy:"modal-title",ariaDescribedBy:"modal-body",templateUrl:"templates/modal/turDeleteInstance.html",controller:"ModalDeleteInstanceCtrl",controllerAs:"$ctrl",size:null,appendTo:undefined,resolve:{instanceName:function(){return h.category.name}}});j.result.then(function(l){h.removeInstance=l;h.deletedMessage='Category "'+h.category.name+'" was deleted.';h.category.$delete(function(){c.addNotification(h.deletedMessage);a.go("ml.datagroup")})},function(){})}}]);turingApp.controller("TurMLCategoryNewCtrl",["$uibModalInstance","category","turMLCategoryResource","turNotificationService",function(d,c,a,e){var b=this;b.removeInstance=false;b.category=c;b.ok=function(){a.save(b.category,function(){e.addNotification('Category "'+b.category.name+'" was created.');d.close(c)})};b.cancel=function(){d.dismiss("cancel")}}]);turingApp.factory("turMLCategoryResource",["$resource",function(a){return a("/turing/api/ml/category/:id",{id:"@id"},{update:{method:"PUT"}})}]);turingApp.controller("TurMLDataEditCtrl",["$scope","$stateParams","$state","$rootScope","$translate","vigLocale","turMLDataResource","turNotificationService","$uibModal",function(h,g,a,f,b,i,e,c,d){h.vigLanguage=i.getLocale().substring(0,2);b.use(h.vigLanguage);f.$state=a;h.data=e.get({id:g.mlDataId});h.dataSave=function(){h.data.$update(function(){c.addNotification('Data "'+h.data.name+'" was saved.')})};h.dataDelete=function(){var k=this;var j=d.open({animation:true,ariaLabelledBy:"modal-title",ariaDescribedBy:"modal-body",templateUrl:"templates/modal/turDeleteInstance.html",controller:"ModalDeleteInstanceCtrl",controllerAs:"$ctrl",size:null,appendTo:undefined,resolve:{instanceName:function(){return h.data.name}}});j.result.then(function(l){h.removeInstance=l;h.deletedMessage='Data "'+h.data.name+'" was deleted.';h.data.$delete(function(){c.addNotification(h.deletedMessage);a.go("ml.datagroup")})},function(){})}}]);turingApp.controller("TurMLDataNewCtrl",["$uibModalInstance","data","fileUpload",function(c,d,b){var a=this;a.myFile=null;a.removeInstance=false;a.data=d;a.ok=function(){var f=a.myFile;var e="/turing/api/ml/data/import";b.uploadFileToUrl(f,e);c.close(d)};a.cancel=function(){a.removeInstance=false;c.dismiss("cancel")}}]);turingApp.controller("TurMLDataSentenceCtrl",["$scope","$stateParams","$state","$rootScope","$translate","vigLocale","$uibModal","turMLCategoryResource","turMLDataSentenceResource","turNotificationService",function(i,h,a,g,b,j,d,e,f,c){i.vigLanguage=j.getLocale().substring(0,2);b.use(i.vigLanguage);g.$state=a;i.categories=e.query();i.sentenceUpdate=function(k){f.update({id:k.id},k,function(){c.addNotification('Sentence "'+k.sentence.substring(0,20)+'..." was saved.')})}}]);turingApp.factory("turMLDataResource",["$resource",function(a){return a("/turing/api/ml/data/:id",{id:"@id"},{update:{method:"PUT"}})}]);turingApp.controller("TurMLDataGroupCategoryCtrl",["$scope","$stateParams","$state","$rootScope","$translate","vigLocale","turMLCategoryResource","$uibModal",function(d,g,f,b,h,e,a,c){d.vigLanguage=e.getLocale().substring(0,2);h.use(d.vigLanguage);b.$state=f;d.categories=a.query();d.categoryNew=function(){var j=this;d.category={};var i=c.open({animation:true,ariaLabelledBy:"modal-title",ariaDescribedBy:"modal-body",templateUrl:"templates/ml/category/ml-category-new.html",controller:"TurMLCategoryNewCtrl",controllerAs:"$ctrl",size:null,appendTo:undefined,resolve:{category:function(){return d.category}}});i.result.then(function(k){},function(){})}}]);turingApp.controller("TurMLDataGroupCtrl",["$scope","$http","$window","$state","$rootScope","$translate","turMLDataGroupResource",function(c,g,f,d,a,e,b){a.$state=d;c.mlDataGroups=b.query()}]);turingApp.controller("TurMLDataGroupDataCtrl",["$scope","$stateParams","$state","$rootScope","$translate","vigLocale","turMLDataResource","$uibModal",function(d,g,f,a,h,e,c,b){d.vigLanguage=e.getLocale().substring(0,2);h.use(d.vigLanguage);a.$state=f;d.datas=c.query();d.uploadDocument=function(){var j=this;d.data={};var i=b.open({animation:true,ariaLabelledBy:"modal-title",ariaDescribedBy:"modal-body",templateUrl:"templates/ml/data/ml-document-upload.html",controller:"TurMLDataNewCtrl",controllerAs:"$ctrl",size:null,appendTo:undefined,resolve:{data:function(){return d.data}}});i.result.then(function(k){console.log(k.name);console.log(k.description)},function(){})}}]);turingApp.controller("TurMLDataGroupEditCtrl",["$scope","$stateParams","$state","$rootScope","$translate","vigLocale","turMLDataGroupResource","turNotificationService","$uibModal",function(h,g,a,f,b,i,e,c,d){h.vigLanguage=i.getLocale().substring(0,2);b.use(h.vigLanguage);f.$state=a;h.dataGroup=e.get({id:g.mlDataGroupId});h.dataGroupSave=function(){h.dataGroup.$update(function(){c.addNotification('Data Group "'+h.dataGroup.name+'" was saved.')})};h.dataGroupDelete=function(){var k=this;var j=d.open({animation:true,ariaLabelledBy:"modal-title",ariaDescribedBy:"modal-body",templateUrl:"templates/modal/turDeleteInstance.html",controller:"ModalDeleteInstanceCtrl",controllerAs:"$ctrl",size:null,appendTo:undefined,resolve:{instanceName:function(){return h.dataGroup.name}}});j.result.then(function(l){h.removeInstance=l;h.deletedMessage='Data Group "'+h.dataGroup.name+'" was deleted.';h.dataGroup.$delete(function(){c.addNotification(h.deletedMessage);a.go("ml.datagroup")})},function(){})}}]);turingApp.controller("TurMLDataGroupNewCtrl",["$scope","$http","$window","$stateParams","$state","$rootScope","$translate","vigLocale","turMLDataGroupResource","turNotificationService",function(i,f,c,h,a,g,b,j,e,d){i.vigLanguage=j.getLocale().substring(0,2);b.use(i.vigLanguage);g.$state=a;i.mlDataGroupId=h.mlDataGroupId;i.dataGroup={};i.dataGroupSave=function(){e.save(i.dataGroup,function(){d.addNotification('Data Group "'+i.dataGroup.name+'" was created.');a.go("ml.datagroup")})}}]);turingApp.factory("turMLDataGroupResource",["$resource",function(a){return a("/turing/api/ml/data/group/:id",{id:"@id"},{update:{method:"PUT"}})}]);turingApp.factory("turMLDataSentenceResource",["$resource",function(a){return a("/turing/api/ml/data/sentence/:id",{id:"@id"},{update:{method:"PUT"}})}]);turingApp.controller("TurMLInstanceCtrl",["$scope","$state","$rootScope","$translate","turMLInstanceResource",function(b,c,a,d,e){a.$state=c;b.mls=e.query()}]);turingApp.controller("TurMLInstanceEditCtrl",["$scope","$stateParams","$state","$rootScope","$translate","vigLocale","turMLInstanceResource","turMLVendorResource","turLocaleResource","turNotificationService","$uibModal",function(j,i,a,h,c,k,g,b,f,d,e){j.vigLanguage=k.getLocale().substring(0,2);c.use(j.vigLanguage);h.$state=a;j.locales=f.query();j.mlVendors=b.query();j.ml=g.get({id:i.mlInstanceId});j.mlInstanceUpdate=function(){j.ml.$update(function(){d.addNotification('Machine Learning Instance "'+j.ml.title+'" was saved.')})};j.mlInstanceDelete=function(){var m=this;var l=e.open({animation:true,ariaLabelledBy:"modal-title",ariaDescribedBy:"modal-body",templateUrl:"templates/modal/turDeleteInstance.html",controller:"ModalDeleteInstanceCtrl",controllerAs:"$ctrl",size:null,appendTo:undefined,resolve:{instanceName:function(){return j.ml.title}}});l.result.then(function(n){j.removeInstance=n;j.deletedMessage='Machine Learning Instance "'+j.ml.title+'" was deleted.';j.ml.$delete(function(){d.addNotification(j.deletedMessage);a.go("ml.instance")})},function(){})}}]);turingApp.controller("TurMLInstanceNewCtrl",["$scope","$state","$rootScope","$translate","vigLocale","turMLInstanceResource","turMLVendorResource","turLocaleResource","turNotificationService",function(h,a,g,c,i,f,b,e,d){h.vigLanguage=i.getLocale().substring(0,2);c.use(h.vigLanguage);g.$state=a;h.locales=e.query();h.mlVendors=b.query();h.ml={enabled:0};h.mlInstanceSave=function(){f.save(h.ml,function(){d.addNotification('Machine Learning Instance "'+h.ml.title+'" was created.');a.go("ml.instance")})}}]);turingApp.factory("turMLInstanceResource",["$resource",function(a){return a("/turing/api/ml/:id",{id:"@id"},{update:{method:"PUT"}})}]);turingApp.controller("TurMLModelCtrl",["$scope","$http","$window","$state","$rootScope","$translate","turMLModelResource",function(b,g,f,c,a,d,e){a.$state=c;b.mlModels=e.query()}]);turingApp.factory("turMLModelResource",["$resource",function(a){return a("/turing/api/ml/model/:id",{id:"@id"},{update:{method:"PUT"}})}]);turingApp.factory("turMLVendorResource",["$resource",function(a){return a("/turing/api/ml/vendor/:id",{id:"@id"},{update:{method:"PUT"}})}]);turingApp.controller("TurNLPEntityCtrl",["$scope","$http","$window","$state","$rootScope","$translate","turNLPEntityResource",function(b,g,e,c,a,d,f){a.$state=c;b.entities=f.query()}]);turingApp.controller("TurNLPEntityEditCtrl",["$scope","$http","$window","$stateParams","$state","$rootScope","$translate","vigLocale","turNLPEntityResource",function(h,e,c,g,a,f,b,i,d){h.vigLanguage=i.getLocale().substring(0,2);b.use(h.vigLanguage);f.$state=a;h.nlpEntityId=g.nlpEntityId;h.entity=d.get({id:h.nlpEntityId})}]);turingApp.factory("turNLPEntityResource",["$resource",function(a){return a("/turing/api/entity/:id",{id:"@id"},{update:{method:"PUT"}})}]);turingApp.controller("TurNLPInstanceCtrl",["$scope","$http","$window","$state","$rootScope","$translate","turNLPInstanceResource",function(b,g,f,c,a,d,e){a.$state=c;b.nlps=e.query()}]);turingApp.controller("TurNLPInstanceEditCtrl",["$scope","$stateParams","$state","$rootScope","$translate","vigLocale","turNLPInstanceResource","turNLPVendorResource","turLocaleResource","turNotificationService","$uibModal",function(j,i,a,h,b,k,f,g,e,c,d){j.vigLanguage=k.getLocale().substring(0,2);b.use(j.vigLanguage);h.$state=a;j.locales=e.query();j.nlpVendors=g.query();j.nlp=f.get({id:i.nlpInstanceId});j.nlpInstanceUpdate=function(){j.nlp.$update(function(){c.addNotification('NLP Instance "'+j.nlp.title+'" was saved.')})};j.nlpInstanceDelete=function(){var m=this;var l=d.open({animation:true,ariaLabelledBy:"modal-title",ariaDescribedBy:"modal-body",templateUrl:"templates/modal/turDeleteInstance.html",controller:"ModalDeleteInstanceCtrl",controllerAs:"$ctrl",size:null,appendTo:undefined,resolve:{instanceName:function(){return j.nlp.title}}});l.result.then(function(n){j.removeInstance=n;j.deletedMessage='NLP Instance "'+j.nlp.title+'" was deleted.';j.nlp.$delete(function(){c.addNotification(j.deletedMessage);a.go("nlp.instance")})},function(){})}}]);turingApp.controller("TurNLPInstanceNewCtrl",["$scope","$state","$rootScope","$translate","vigLocale","turNLPInstanceResource","turNLPVendorResource","turLocaleResource","turNotificationService",function(h,a,g,b,i,e,f,d,c){h.vigLanguage=i.getLocale().substring(0,2);b.use(h.vigLanguage);g.$state=a;h.locales=d.query();h.nlpVendors=f.query();h.nlp={enabled:0};h.nlpInstanceSave=function(){e.save(h.nlp,function(){c.addNotification('NLP Instance "'+h.nlp.title+'" was created.');a.go("nlp.instance")})}}]);turingApp.factory("turNLPInstanceResource",["$resource",function(a){return a("/turing/api/nlp/:id",{id:"@id"},{update:{method:"PUT"}})}]);turingApp.controller("TurNLPValidationCtrl",["$scope","$http","$window","$state","$rootScope","$translate","turNLPInstanceResource",function(b,g,f,c,a,d,e){b.results=null;b.text=null;b.nlpmodel=null;a.$state=c;b.nlps=e.query({},function(){angular.forEach(b.nlps,function(i,h){if(i.selected==true){b.nlpmodel=i.id}})});b.changeView=function(h){text={text:b.text};var i=JSON.stringify(text);g.post("/turing/api/nlp/"+b.nlpmodel+"/validate",i).then(function(j){b.results=j.data},function(j){})}}]);turingApp.factory("turNLPVendorResource",["$resource",function(a){return a("/turing/api/nlp/vendor/:id",{id:"@id"},{update:{method:"PUT"}})}]);turingApp.controller("TurSEInstanceCtrl",["$scope","$http","$window","$state","$rootScope","$translate","turSEInstanceResource",function(b,g,f,d,a,e,c){a.$state=d;b.ses=c.query()}]);turingApp.controller("TurSEInstanceEditCtrl",["$scope","$stateParams","$state","$rootScope","$translate","vigLocale","turSEInstanceResource","turSEVendorResource","turLocaleResource","turNotificationService","$uibModal",function(j,i,a,h,b,k,d,c,g,e,f){j.vigLanguage=k.getLocale().substring(0,2);b.use(j.vigLanguage);h.$state=a;j.locales=g.query();j.seVendors=c.query();j.se=d.get({id:i.seInstanceId});j.seInstanceUpdate=function(){j.se.$update(function(){e.addNotification('Search Engine Instance "'+j.se.title+'" was saved.')})};j.seInstanceDelete=function(){var m=this;var l=f.open({animation:true,ariaLabelledBy:"modal-title",ariaDescribedBy:"modal-body",templateUrl:"templates/modal/turDeleteInstance.html",controller:"ModalDeleteInstanceCtrl",controllerAs:"$ctrl",size:null,appendTo:undefined,resolve:{instanceName:function(){return j.se.title}}});l.result.then(function(n){j.removeInstance=n;j.deletedMessage='Search Engine Instance "'+j.se.title+'" was deleted.';j.se.$delete(function(){e.addNotification(j.deletedMessage);a.go("se.instance")})},function(){})}}]);turingApp.controller("TurSEInstanceNewCtrl",["$scope","$state","$rootScope","$translate","vigLocale","turSEInstanceResource","turSEVendorResource","turLocaleResource","turNotificationService",function(h,a,g,b,i,d,c,f,e){h.vigLanguage=i.getLocale().substring(0,2);b.use(h.vigLanguage);g.$state=a;h.locales=f.query();h.seVendors=c.query();h.se={enabled:0};h.seInstanceSave=function(){d.save(h.se,function(){e.addNotification('Search Engine Instance "'+h.se.title+'" was created.');a.go("se.instance")})}}]);turingApp.factory("turSEInstanceResource",["$resource",function(a){return a("/turing/api/se/:id",{id:"@id"},{update:{method:"PUT"}})}]);turingApp.factory("turSEVendorResource",["$resource",function(a){return a("/turing/api/se/vendor/:id",{id:"@id"},{update:{method:"PUT"}})}]);turingApp.controller("TurSNAdvertisingCtrl",["$scope","$http","$window","$state","$rootScope","$translate",function(b,f,e,c,a,d){a.$state=c}]);turingApp.controller("TurSNSiteCtrl",["$scope","$http","$window","$state","$rootScope","$translate","turSNSiteResource",function(b,g,f,c,a,d,e){a.$state=c;b.snSites=e.query()}]);turingApp.controller("TurSNSiteEditCtrl",["$scope","$stateParams","$state","$rootScope","$translate","vigLocale","turSNSiteResource","turSEInstanceResource","turNLPInstanceResource","turNotificationService","$uibModal",function(j,i,a,h,b,k,g,c,f,d,e){j.vigLanguage=k.getLocale().substring(0,2);b.use(j.vigLanguage);h.$state=a;j.seInstances=c.query();j.nlpInstances=f.query();j.snSite=g.get({id:i.snSiteId});j.snSiteUpdate=function(){j.snSite.$update(function(){d.addNotification('Semantic Navigation Site "'+j.snSite.name+'" was saved.')})};j.snSiteDelete=function(){var m=this;var l=e.open({animation:true,ariaLabelledBy:"modal-title",ariaDescribedBy:"modal-body",templateUrl:"templates/modal/turDeleteInstance.html",controller:"ModalDeleteInstanceCtrl",controllerAs:"$ctrl",size:null,appendTo:undefined,resolve:{instanceName:function(){d.addNotification('Semantic Navigation Site "'+j.snSite.name+'" was deleted.');return j.snSite.name}}});l.result.then(function(n){j.removeInstance=n;j.snSite.$delete(function(){a.go("sn.site")})},function(){})}}]);turingApp.controller("TurSNSiteNewCtrl",["$scope","$state","$rootScope","$translate","vigLocale","turSNSiteResource","turSEInstanceResource","turNLPInstanceResource","turNotificationService",function(h,a,g,b,i,f,c,e,d){h.vigLanguage=i.getLocale().substring(0,2);b.use(h.vigLanguage);g.$state=a;h.snSite={};h.seInstances=c.query({},function(){angular.forEach(h.seInstances,function(k,j){if(k.selected==true){k.title=k.title;h.snSite.turSEInstance=k}})});h.nlpInstances=e.query({},function(){angular.forEach(h.nlpInstances,function(k,j){if(k.selected==true){k.title=k.title;h.snSite.turNLPInstance=k}})});h.snSiteSave=function(){f.save(h.snSite,function(){d.addNotification('Semantic Navigation Site "'+h.snSite.name+'" was created.');a.go("sn.site")})}}]);turingApp.factory("turSNSiteResource",["$resource",function(a){return a("/turing/api/sn/:id",{id:"@id"},{update:{method:"PUT"}})}]);turingApp.controller("ModalDeleteInstanceCtrl",["$uibModalInstance","instanceName",function(c,a){var b=this;b.removeInstance=false;b.instanceName=a;b.ok=function(){b.removeInstance=true;c.close(b.removeInstance)};b.cancel=function(){b.removeInstance=false;c.dismiss("cancel")}}]);turingApp.controller("TurAlertCtrl",["$scope","turNotificationService",function(a,b){a.alerts=b.notifications;a.closeAlert=function(c){b.notifications.splice(c,1)}}]);turingApp.factory("turLocaleResource",["$resource",function(a){return a("/turing/api/locale/:id",{id:"@id"},{update:{method:"PUT"}})}]);turingApp.config(["$stateProvider","$urlRouterProvider","$locationProvider","$translateProvider",function(c,a,b,d){d.useSanitizeValueStrategy("escaped");d.translations("en",{NLP_EDIT:"Edit NLP",NLP_EDIT_SUBTITLE:"Change the NLP Settings",NAME:"Name",DESCRIPTION:"Description",VENDORS:"Vendors",HOST:"Host",PORT:"Port",SETTINGS_SAVE_CHANGES:"Save Changes",INTERNAL_NAME:"Internal Name"});d.translations("pt",{NLP_EDIT:"Editar o NLP",NLP_EDIT_SUBTITLE:"Altere as configurações do NLP",NAME:"Nome",DESCRIPTION:"Descrição",VENDORS:"Produtos",HOST:"Host",PORT:"Porta",SETTINGS_SAVE_CHANGES:"Salvar Alterações",INTERNAL_NAME:"Nome Interno"});d.fallbackLanguage("en");a.otherwise("/home");c.state("home",{url:"/home",templateUrl:"templates/home.html",controller:"TurHomeCtrl",data:{pageTitle:"Home | Viglet Turing"}}).state("ml",{url:"/ml",templateUrl:"templates/ml/ml.html",data:{pageTitle:"Machine Learning | Viglet Turing"}}).state("ml.instance",{url:"/instance",templateUrl:"templates/ml/ml-instance.html",controller:"TurMLInstanceCtrl",data:{pageTitle:"Machine Learnings | Viglet Turing"}}).state("ml.instance-new",{url:"/instance/new",templateUrl:"templates/ml/ml-instance-new.html",controller:"TurMLInstanceNewCtrl",data:{pageTitle:"New Machine Learning Instance | Viglet Turing"}}).state("ml.instance-edit",{url:"/instance/:mlInstanceId",templateUrl:"templates/ml/ml-instance-edit.html",controller:"TurMLInstanceEditCtrl",data:{pageTitle:"Edit Machine Learning | Viglet Turing"}}).state("ml.category-edit",{url:"/category/:mlCategoryId",templateUrl:"templates/ml/category/ml-category-edit.html",controller:"TurMLCategoryEditCtrl",data:{pageTitle:"Edit Category | Viglet Turing"}}).state("ml.model",{url:"/model",templateUrl:"templates/ml/model/ml-model.html",controller:"TurMLModelCtrl",data:{pageTitle:"Machine Learning Models | Viglet Turing"}}).state("ml.data-edit",{url:"/data/:mlDataId",templateUrl:"templates/ml/data/ml-data-edit.html",controller:"TurMLDataEditCtrl",data:{pageTitle:"Edit Data | Viglet Turing"}}).state("ml.data-edit.sentence",{url:"/sentence",templateUrl:"templates/ml/data/ml-data-sentence.html",controller:"TurMLDataSentenceCtrl",data:{pageTitle:"Edit Data | Viglet Turing"}}).state("ml.datagroup",{url:"/datagroup",templateUrl:"templates/ml/data/group/ml-datagroup.html",controller:"TurMLDataGroupCtrl",data:{pageTitle:"Machine Learning Data Groups | Viglet Turing"}}).state("ml.datagroup-new",{url:"/datagroup/new",templateUrl:"templates/ml/data/group/ml-datagroup-new.html",controller:"TurMLDataGroupNewCtrl",data:{pageTitle:"New Data Group | Viglet Turing"}}).state("ml.datagroup-edit",{url:"/datagroup/:mlDataGroupId",templateUrl:"templates/ml/data/group/ml-datagroup-edit.html",controller:"TurMLDataGroupEditCtrl",data:{pageTitle:"Edit Data Group | Viglet Turing"}}).state("ml.datagroup-edit.category",{url:"/category",templateUrl:"templates/ml/data/group/ml-datagroup-category.html",controller:"TurMLDataGroupCategoryCtrl",data:{pageTitle:"Data Group Categories | Viglet Turing"}}).state("ml.datagroup-edit.data",{url:"/document",templateUrl:"templates/ml/data/group/ml-datagroup-data.html",controller:"TurMLDataGroupDataCtrl",data:{pageTitle:"Data Group Documents | Viglet Turing"}}).state("se",{url:"/se",templateUrl:"templates/se/se.html",data:{pageTitle:"Search Engine | Viglet Turing"}}).state("se.instance",{url:"/instance",templateUrl:"templates/se/se-instance.html",controller:"TurSEInstanceCtrl",data:{pageTitle:"Search Engines | Viglet Turing"}}).state("se.instance-new",{url:"/instance/new",templateUrl:"templates/se/se-instance-new.html",controller:"TurSEInstanceNewCtrl",data:{pageTitle:"New Search Engine Instance | Viglet Turing"}}).state("se.instance-edit",{url:"/instance/:seInstanceId",templateUrl:"templates/se/se-instance-edit.html",controller:"TurSEInstanceEditCtrl",data:{pageTitle:"Edit Search Engine | Viglet Turing"}}).state("sn",{url:"/sn",templateUrl:"templates/sn/sn.html",data:{pageTitle:"Semantic Navigation | Viglet Turing"}}).state("sn.site",{url:"/site",templateUrl:"templates/sn/sn-site.html",controller:"TurSNSiteCtrl",data:{pageTitle:"Semantic Navigation Sites | Viglet Turing"}}).state("sn.site-new",{url:"/site/new",templateUrl:"templates/sn/sn-site-new.html",controller:"TurSNSiteNewCtrl",data:{pageTitle:"New Semantic Navigation Site | Viglet Turing"}}).state("sn.site-edit",{url:"/site/:snSiteId",templateUrl:"templates/sn/sn-site-edit.html",controller:"TurSNSiteEditCtrl",data:{pageTitle:"Edit Semantic Navigation Site | Viglet Turing"}}).state("sn.ad",{url:"/ad",templateUrl:"templates/sn/sn-ad.html",controller:"TurSNAdvertisingCtrl",data:{pageTitle:"Semantic Navigation Advertising | Viglet Turing"}}).state("nlp",{url:"/nlp",templateUrl:"templates/nlp/nlp.html",data:{pageTitle:"NLP | Viglet Turing"}}).state("nlp.instance",{url:"/instance",templateUrl:"templates/nlp/nlp-instance.html",controller:"TurNLPInstanceCtrl",data:{pageTitle:"NLPs | Viglet Turing"}}).state("nlp.instance-new",{url:"/instance/new",templateUrl:"templates/nlp/nlp-instance-new.html",controller:"TurNLPInstanceNewCtrl",data:{pageTitle:"New NLP Instance | Viglet Turing"}}).state("nlp.instance-edit",{url:"/instance/:nlpInstanceId",templateUrl:"templates/nlp/nlp-instance-edit.html",controller:"TurNLPInstanceEditCtrl",data:{pageTitle:"Edit NLP | Viglet Turing"}}).state("nlp.validation",{url:"/validation",templateUrl:"templates/nlp/nlp-validation.html",controller:"TurNLPValidationCtrl",data:{pageTitle:"NLP Validation | Viglet Turing"}}).state("nlp.entity",{url:"/entity",templateUrl:"templates/nlp/entity/nlp-entity.html",controller:"TurNLPEntityCtrl",data:{pageTitle:"NLP Entities | Viglet Turing"}}).state("nlp.entity-import",{url:"/entity/import",templateUrl:"templates/nlp/entity/nlp-entity-import.html",data:{pageTitle:"Import Entity | Viglet Turing"}}).state("nlp.entity-edit",{url:"/entity/:nlpEntityId",templateUrl:"templates/nlp/entity/nlp-entity-edit.html",controller:"TurNLPEntityEditCtrl",data:{pageTitle:"Edit Entity | Viglet Turing"}}).state("nlp.entity-edit.term",{url:"/term",templateUrl:"templates/nlp/entity/nlp-entity-term.html",data:{pageTitle:"Entity Terms | Viglet Turing"}})}]);