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

package com.viglet.turing.connector.plugin.webcrawler;

import com.google.inject.Inject;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.connector.plugin.TurConnectorPlugin;
import com.viglet.turing.connector.plugin.webcrawler.persistence.repository.TurWCSourceRepository;

public class TurWCPlugin implements TurConnectorPlugin {
    private final TurWCSourceRepository turWCSourceRepository;
    private final TurWCPluginProcess turWCPluginProcess;

    @Inject
    public TurWCPlugin(TurWCSourceRepository turWCSourceRepository, TurWCPluginProcess turWCPluginProcess) {
        this.turWCSourceRepository = turWCSourceRepository;
        this.turWCPluginProcess = turWCPluginProcess;
    }

    public void init() {
        turWCSourceRepository.findAll().forEach(turWCPluginProcess::start);
    }
    @Override
    public TurSNJobItem getNext() {
        return turWCPluginProcess.getNext();
    }
}