package com.viglet.turing.connector.aem.indexer.onstartup.customer;

import com.google.inject.Inject;
import com.viglet.turing.connector.aem.indexer.persistence.model.TurAemConfigVar;
import com.viglet.turing.connector.aem.indexer.persistence.repository.TurAemConfigVarRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@Transactional
public class TurAemOnStartupJpa implements ApplicationRunner {
    public static final String FIRST_TIME = "FIRST_TIME";
    private final TurAemConfigVarRepository turAemConfigVarRepository;

    @Inject
    public TurAemOnStartupJpa(TurAemConfigVarRepository turAemConfigVarRepository) {
        this.turAemConfigVarRepository = turAemConfigVarRepository;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (turAemConfigVarRepository.findById(FIRST_TIME).isEmpty()) {
            log.info("First Time Configuration ...");
            turAemConfigVarRepository.saveAll(List.of(
                    new TurAemConfigVar("turing.url", "/system", "http://localhost:2700"),
                    new TurAemConfigVar("turing.apiKey", "/system", "4618ac5e0e5640f8bd8ea8c83"),
                    new TurAemConfigVar("turing.mapping.file", "/system", "content-mapping-insper.json"),
                    new TurAemConfigVar("turing.provider.name", "/system", "AEM"),
                    new TurAemConfigVar("cms.url", "/system", "https://author-p120653-e1241062.adobeaemcloud.com"),
                    new TurAemConfigVar("cms.username", "/system", "vilttester"),
                    new TurAemConfigVar("cms.password", "/system", "Insper@2023!"),
                    new TurAemConfigVar("cms.content-type", "/system", "cq:Page"),
                    new TurAemConfigVar("cms.root.path", "/system", "/content/insper-portal"),
                    new TurAemConfigVar("cms.group", "/system", "INSPER-DEV-AUTHOR"),
                    new TurAemConfigVar("cms.dryRun", "/system", "false"),
                    new TurAemConfigVar("dps.site.default.sn.site", "/system", "insper-dev-author"),
                    new TurAemConfigVar("dps.site.default.sn.locale", "/system", "pt_BR"),
                    new TurAemConfigVar("dps.site.default.url.prefix", "/system", "https://author-p120653-e1241062.adobeaemcloud.com"),
                    new TurAemConfigVar("sn.default.once.pattern.path", "/system", "^/content/insper-portal/pt/noticias/20(08|09|10|11|12|13|14|15|16|17|18|19|20|21|22|23)"),
                    new TurAemConfigVar("sn.insper-dev-author.en_US.path", "/system", "/content/insper-portal/en"),
                    new TurAemConfigVar("sn.insper-dev-author.pt_BR.path", "/system", "/content/insper-portal/pt")
            ));
            setFirstTIme();
            log.info("Configuration finished.");
        }
    }

    private void setFirstTIme() {
        TurAemConfigVar turConfigVar = new TurAemConfigVar(FIRST_TIME, "/system", "true");
        turAemConfigVarRepository.save(turConfigVar);
    }
}
