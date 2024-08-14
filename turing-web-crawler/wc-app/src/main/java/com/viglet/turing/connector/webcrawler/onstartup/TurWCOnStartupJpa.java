package com.viglet.turing.connector.webcrawler.onstartup;

import com.google.inject.Inject;
import com.viglet.turing.connector.webcrawler.export.TurWCExchangeProcess;
import com.viglet.turing.connector.webcrawler.persistence.model.TurWCConfigVar;
import com.viglet.turing.connector.webcrawler.persistence.repository.TurWCConfigVarRepository;
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
public class TurWCOnStartupJpa implements ApplicationRunner {
    public static final String FIRST_TIME = "FIRST_TIME";

    private final TurWCConfigVarRepository turWCConfigVarRepository;
    private final TurWCExchangeProcess turWCExchangeProcess;

    @Inject
    public TurWCOnStartupJpa(TurWCConfigVarRepository turWCConfigVarRepository,
                             TurWCExchangeProcess turWCExchangeProcess) {
        this.turWCConfigVarRepository = turWCConfigVarRepository;
        this.turWCExchangeProcess = turWCExchangeProcess;
    }

    @Override
    public void run(ApplicationArguments arg0) {
        if (this.turWCConfigVarRepository.findById(FIRST_TIME).isEmpty()) {
            System.out.println("First Time Configuration ...");
            Path exportFile = Paths.get("export/export.json");
            if (exportFile.toFile().exists()) {
                turWCExchangeProcess.importFromFile(exportFile.toFile());
            }
            setFirstTIme();
            System.out.println("Configuration finished.");
        }
    }

    private void setFirstTIme() {
        TurWCConfigVar turConfigVar = new TurWCConfigVar();
        turConfigVar.setId(FIRST_TIME);
        turConfigVar.setPath("/system");
        turConfigVar.setValue("true");
        this.turWCConfigVarRepository.save(turConfigVar);
    }
}
