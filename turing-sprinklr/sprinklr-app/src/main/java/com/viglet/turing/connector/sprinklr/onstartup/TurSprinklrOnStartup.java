package com.viglet.turing.connector.sprinklr.onstartup;

import com.google.inject.Inject;
import com.viglet.turing.connector.sprinklr.export.TurSprinklrExchangeProcess;
import com.viglet.turing.connector.sprinklr.persistence.model.TurSprinklrConfigVar;
import com.viglet.turing.connector.sprinklr.persistence.repository.TurSprinklrConfigVarRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Component
@Transactional
public class TurSprinklrOnStartup implements ApplicationRunner {
    public static final String FIRST_TIME = "FIRST_TIME";
    private final TurSprinklrConfigVarRepository turSprinklrConfigVarRepository;
    private final TurSprinklrExchangeProcess turSprinklrExchangeProcess;

    @Inject
    public TurSprinklrOnStartup(TurSprinklrConfigVarRepository turSprinklrConfigVarRepository,
                                   TurSprinklrExchangeProcess turSprinklrExchangeProcess) {
        this.turSprinklrConfigVarRepository = turSprinklrConfigVarRepository;
        this.turSprinklrExchangeProcess = turSprinklrExchangeProcess;
    }

    @Override
    public void run(ApplicationArguments arg0) {
        if (this.turSprinklrConfigVarRepository.findById(FIRST_TIME).isEmpty()) {
           log.info("First Time Configuration ...");
            Path exportFile = Paths.get("export/export.json");
            if (exportFile.toFile().exists()) {
                turSprinklrExchangeProcess.importFromFile(exportFile.toFile());
            }
            setFirstTIme();
            log.info("Configuration finished.");
        }
    }

    private void setFirstTIme() {
        TurSprinklrConfigVar turConfigVar = new TurSprinklrConfigVar();
        turConfigVar.setId(FIRST_TIME);
        turConfigVar.setPath("/system");
        turConfigVar.setValue("true");
        this.turSprinklrConfigVarRepository.save(turConfigVar);
    }
}
