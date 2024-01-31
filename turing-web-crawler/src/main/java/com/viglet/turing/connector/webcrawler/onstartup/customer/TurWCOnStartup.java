package com.viglet.turing.connector.webcrawler.onstartup.customer;

import com.google.inject.Inject;
import com.viglet.turing.connector.webcrawler.TurWCProcess;
import com.viglet.turing.connector.webcrawler.TurWCCustomClass;
import com.viglet.turing.connector.webcrawler.TurWCCustomDocument;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.LocaleUtils;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@Transactional
public class TurWCOnStartup implements ApplicationRunner {
    private final TurWCProcess turWCProcess;

    @Inject
    public TurWCOnStartup(TurWCProcess turWCProcess) {
        this.turWCProcess = turWCProcess;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        indexInsperEE();
        indexInsper();
    }

    private void indexInsper() {
        final List<String> allowUrls = new ArrayList<>();
        final List<String> notAllowUrls = new ArrayList<>();
        final List<String> notAllowExtensions = new ArrayList<>();

        List<TurWCCustomClass> turWCCustomClasses = new ArrayList<>();
        turWCCustomClasses.add(TurWCCustomClass.builder()
                .attribute("site")
                .text("Insper Portal")
                .build());
        turWCCustomClasses.add(TurWCCustomClass.builder()
                .attribute("id")
                .className("com.viglet.turing.connector.webcrawler.ext.TurWCExtId")
                .build());
        turWCCustomClasses.add(TurWCCustomClass.builder()
                .attribute("title")
                .className("com.viglet.turing.connector.webcrawler.ext.customer.TurInsperTitle")
                .build());
        turWCCustomClasses.add(TurWCCustomClass.builder()
                .attribute("publicationDate")
                .className("com.viglet.turing.connector.webcrawler.ext.customer.TurInsperDate")
                .build());
        turWCCustomClasses.add(TurWCCustomClass.builder()
                .attribute("modificationDate")
                .className("com.viglet.turing.connector.webcrawler.ext.customer.TurInsperDate")
                .build());
        turWCCustomClasses.add(TurWCCustomClass.builder()
                .attribute("text")
                .className("com.viglet.turing.connector.webcrawler.ext.customer.TurInsperText")
                .build());
        turWCCustomClasses.add(TurWCCustomClass.builder()
                .attribute("abstract")
                .className("com.viglet.turing.connector.webcrawler.ext.customer.TurInsperAbstract")
                .build());
        turWCCustomClasses.add(TurWCCustomClass.builder()
                .attribute("source_apps")
                .text("WEM-CRAWLER")
                .build());
        turWCCustomClasses.add(TurWCCustomClass.builder()
                .attribute("url")
                .className("com.viglet.turing.connector.webcrawler.ext.TurWCExtUrl")
                .build());
        turWCCustomClasses.add(TurWCCustomClass.builder()
                .attribute("instituicoes")
                .text("Insper Instituto")
                .build());
        allowUrls.add("");
        notAllowExtensions.add(".pdf");
        notAllowExtensions.add(".xls");
        notAllowExtensions.add(".xlsx");
        notAllowExtensions.add(".doc");
        notAllowExtensions.add(".m4a");
        notAllowExtensions.add(".zip");
        notAllowExtensions.add(".png");


        notAllowUrls.add("/graduacao");
        notAllowUrls.add("/pos-graduacao");
        notAllowUrls.add("/certificacoes");
        notAllowUrls.add("/educacao-executiva");
        notAllowUrls.add("/noticias");
        notAllowUrls.add("/agenda-de-eventos");
        notAllowUrls.add("/pesquisa-e-conhecimento/docentes-pesquisadores");
        notAllowUrls.add("/pesquisa-e-conhecimento/corpo-docente");

        notAllowUrls.add("/en/undergraduate");
        notAllowUrls.add("/en/graduate");
        notAllowUrls.add("/en/live-learning");
        notAllowUrls.add("/en/insper-news");
        notAllowUrls.add("/en/events");
        notAllowUrls.add("/en/faculty/research-professor");
        notAllowUrls.add("/en/faculty/professors");

        turWCProcess.start("https://www.insper.edu.br","insper-dev-author",
                TurWCCustomDocument.builder()
                        .localeClass("com.viglet.turing.connector.webcrawler.ext.customer.TurInsperLocale")
                        .attributes(turWCCustomClasses)
                        .build(),
                allowUrls, notAllowUrls, notAllowExtensions);

    }

    private void indexInsperEE() {
        final List<String> allowUrls = new ArrayList<>();
        final List<String> notAllowUrls = new ArrayList<>();
        final List<String> notAllowExtensions = new ArrayList<>();

        List<TurWCCustomClass> turWCCustomClassesEE = new ArrayList<>();
        turWCCustomClassesEE.add(TurWCCustomClass.builder()
                .attribute("site")
                .text("Insper Portal")
                .build());
        turWCCustomClassesEE.add(TurWCCustomClass.builder()
                .attribute("id")
                .className("com.viglet.turing.connector.webcrawler.ext.TurWCExtId")
                .build());
        turWCCustomClassesEE.add(TurWCCustomClass.builder()
                .attribute("title")
                .className("com.viglet.turing.connector.webcrawler.ext.TurWCExtTitle")
                .build());
        turWCCustomClassesEE.add(TurWCCustomClass.builder()
                .attribute("publicationDate")
                .className("com.viglet.turing.connector.webcrawler.ext.TurWCExtDate")
                .build());
        turWCCustomClassesEE.add(TurWCCustomClass.builder()
                .attribute("modificationDate")
                .className("com.viglet.turing.connector.webcrawler.ext.TurWCExtDate")
                .build());
        turWCCustomClassesEE.add(TurWCCustomClass.builder()
                .attribute("source_apps")
                .text("WEM-CRAWLER")
                .build());
        turWCCustomClassesEE.add(TurWCCustomClass.builder()
                .attribute("url")
                .className("com.viglet.turing.connector.webcrawler.ext.TurWCExtUrl")
                .build());
        turWCCustomClassesEE.add(TurWCCustomClass.builder()
                .attribute("abstract")
                .className("com.viglet.turing.connector.webcrawler.ext.TurWCExtDescription")
                .build());
        turWCCustomClassesEE.add(TurWCCustomClass.builder()
                .attribute("text")
                .className("com.viglet.turing.connector.webcrawler.ext.customer.TurInsperEEText")
                .build());
        turWCCustomClassesEE.add(TurWCCustomClass.builder()
                .attribute("area-de-conhecimento")
                .className("com.viglet.turing.connector.webcrawler.ext.customer.TurInsperEECategory")
                .build());
        turWCCustomClassesEE.add(TurWCCustomClass.builder()
                .attribute("templateName")
                .text("cursos")
                .build());
        turWCCustomClassesEE.add(TurWCCustomClass.builder()
                .attribute("instituicoes")
                .text("Insper Educação Executiva")
                .build());
        allowUrls.add("/cursos-de-curta-duracao");
        allowUrls.add("/cursos/alta-gestao");
        allowUrls.add("/cursos/direito");
        allowUrls.add("/cursos/empreendedorismo-e-inovacao");
        allowUrls.add("/cursos/empresas-familiares");
        allowUrls.add("/cursos/estrategia-e-negocios");
        allowUrls.add("/cursos/financas");
        allowUrls.add("/cursos/internacional");
        allowUrls.add("/cursos/lideranca-e-pessoas");
        allowUrls.add("/cursos/marketing-e-vendas");
        allowUrls.add("/cursos/saude");
        allowUrls.add("/cursos/tecnologia-data-science");
        allowUrls.add("/cursos-de-curta-duracao/direito");
        allowUrls.add("/cursos-de-curta-duracao/estrategia-e-negocios/");
        turWCProcess.start("https://ee.insper.edu.br","insper-dev-author",
                TurWCCustomDocument.builder()
                        .locale(LocaleUtils.toLocale("pt_BR"))
                        .attributes(turWCCustomClassesEE)
                        .build(),
                allowUrls, notAllowUrls, notAllowExtensions);
    }
}
