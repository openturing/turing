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

package com.viglet.turing.connector.plugin.aem;

import com.viglet.turing.connector.aem.commons.TurAemCommonsUtils;
import com.viglet.turing.connector.commons.plugin.TurConnectorContext;
import com.viglet.turing.connector.commons.plugin.TurConnectorPlugin;
import com.viglet.turing.connector.plugin.aem.persistence.repository.TurAemSourceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TurAemPlugin implements TurConnectorPlugin {
    @Autowired
    private TurAemPluginProcess turAemPluginProcess;
    @Autowired
    private TurAemSourceRepository turAemSourceRepository;

    @Override
    public void init(TurConnectorContext turConnectorContext) {
        turAemSourceRepository.findAll().forEach(turAemSource -> {
            turAemPluginProcess.run(turAemSource, turConnectorContext);
            TurAemCommonsUtils.cleanCache();
        });
    }
}