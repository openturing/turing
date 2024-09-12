/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.connector.aem.onstartup;

import com.google.inject.Inject;
import com.viglet.turing.connector.aem.persistence.model.TurAemConfigVar;
import com.viglet.turing.connector.aem.persistence.repository.TurAemConfigVarRepository;
import com.viglet.turing.connector.aem.export.TurAemExchangeProcess;
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
public class TurAemOnStartupJpa implements ApplicationRunner {
    public static final String FIRST_TIME = "FIRST_TIME";

    private final TurAemConfigVarRepository turAemConfigVarRepository;
    private final TurAemExchangeProcess turAemExchangeProcess;

    @Inject
    public TurAemOnStartupJpa(TurAemConfigVarRepository turAemConfigVarRepository,
                                   TurAemExchangeProcess turAemExchangeProcess) {
        this.turAemConfigVarRepository = turAemConfigVarRepository;
        this.turAemExchangeProcess = turAemExchangeProcess;
    }

    @Override
    public void run(ApplicationArguments arg0) {
        if (this.turAemConfigVarRepository.findById(FIRST_TIME).isEmpty()) {
            System.out.println("First Time Configuration ...");
            Path exportFile = Paths.get("com/viglet/turing/connector/aem/export/export.json");
            if (exportFile.toFile().exists()) {
                turAemExchangeProcess.importFromFile(exportFile.toFile());
            }
            setFirstTIme();
            System.out.println("Configuration finished.");
        }
    }

    private void setFirstTIme() {
        TurAemConfigVar turConfigVar = new TurAemConfigVar();
        turConfigVar.setId(FIRST_TIME);
        turConfigVar.setPath("/system");
        turConfigVar.setValue("true");
        this.turAemConfigVarRepository.save(turConfigVar);
    }
}
