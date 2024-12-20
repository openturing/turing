package com.viglet.turing.connector.onstartup;

import com.google.inject.Inject;
import com.viglet.turing.connector.persistence.model.TurConnectorConfigVar;
import com.viglet.turing.connector.persistence.repository.TurConnectorConfigVarRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Transactional
public class TurConnectorOnStartup implements ApplicationRunner {
    public static final String FIRST_TIME = "FIRST_TIME";

    private final TurConnectorConfigVarRepository turConnectorConfigVarRepository;

    @Inject
    public TurConnectorOnStartup(TurConnectorConfigVarRepository turConnectorConfigVarRepository) {
        this.turConnectorConfigVarRepository = turConnectorConfigVarRepository;
    }

    @Override
    public void run(ApplicationArguments arg0) {
        if (this.turConnectorConfigVarRepository.findById(FIRST_TIME).isEmpty()) {
            log.info("First Time Configuration ...");
            setFirstTIme();
            log.info("Configuration finished.");
        }
    }

    private void setFirstTIme() {
        TurConnectorConfigVar turConfigVar = new TurConnectorConfigVar();
        turConfigVar.setId(FIRST_TIME);
        turConfigVar.setPath("/system");
        turConfigVar.setValue("true");
        this.turConnectorConfigVarRepository.save(turConfigVar);
    }
}
