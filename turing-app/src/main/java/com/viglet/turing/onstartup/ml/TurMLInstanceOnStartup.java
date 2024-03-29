/*
 * Copyright (C) 2016-2022 the original author or authors. 
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.viglet.turing.onstartup.ml;

import com.google.inject.Inject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.model.ml.TurMLInstance;
import com.viglet.turing.persistence.model.system.TurConfigVar;
import com.viglet.turing.persistence.repository.ml.TurMLInstanceRepository;
import com.viglet.turing.persistence.repository.ml.TurMLVendorRepository;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;
import com.viglet.turing.persistence.repository.system.TurLocaleRepository;

@Component
@Transactional
public class TurMLInstanceOnStartup {
	private final TurMLInstanceRepository turMLInstanceRepository;
	private final TurMLVendorRepository turMLVendorRepository;
	private final TurConfigVarRepository turConfigVarRepository;

	@Inject
	public TurMLInstanceOnStartup(TurMLInstanceRepository turMLInstanceRepository,
								  TurMLVendorRepository turMLVendorRepository,
								  TurConfigVarRepository turConfigVarRepository) {
		this.turMLInstanceRepository = turMLInstanceRepository;
		this.turMLVendorRepository = turMLVendorRepository;
		this.turConfigVarRepository = turConfigVarRepository;
	}

	public void createDefaultRows() {

		TurConfigVar turConfigVar = new TurConfigVar();

		if (turMLInstanceRepository.findAll().isEmpty()) {
			turMLVendorRepository.findById("OPENNLP").ifPresent(turMLVendor -> {
				TurMLInstance turMLInstance = new TurMLInstance();
				turMLInstance.setTitle("OpenNLP");
				turMLInstance.setDescription("OpenNLP Production");
				turMLInstance.setTurMLVendor(turMLVendor);
				turMLInstance.setHost("");
				turMLInstance.setPort(0);
				turMLInstance.setLanguage(TurLocaleRepository.EN_US);
				turMLInstance.setEnabled(1);
				turMLInstanceRepository.save(turMLInstance);

				turConfigVar.setId("DEFAULT_ML");
				turConfigVar.setPath("/ml");
				turConfigVar.setValue(Integer.toString(turMLInstance.getId()));
				turConfigVarRepository.save(turConfigVar);
			});
		}

	}

}
