/*
 * Copyright (C) 2016-2020 the original author or authors. 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.onstartup.nlp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.nlp.TurNLPVendorsConstant;
import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.persistence.model.system.TurConfigVar;
import com.viglet.turing.persistence.repository.nlp.TurNLPInstanceRepository;
import com.viglet.turing.persistence.repository.nlp.TurNLPVendorRepository;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;
import com.viglet.turing.persistence.repository.system.TurLocaleRepository;

@Component
@Transactional
public class TurNLPInstanceOnStartup {
	private static final String LOCALHOST = "localhost";
	@Autowired
	private TurNLPInstanceRepository turNLPInstanceRepository;
	@Autowired
	private TurNLPVendorRepository turNLPVendorRepository;
	@Autowired
	private TurConfigVarRepository turConfigVarRepository;

	public void createDefaultRows() {

		TurConfigVar turConfigVar = new TurConfigVar();

		if (turNLPInstanceRepository.findAll().isEmpty()) {

			turNLPVendorRepository.findById(TurNLPVendorsConstant.OPENNLP).ifPresent(turNLPVendorOpenNLP -> {
				TurNLPInstance turNLPInstance = new TurNLPInstance();
				turNLPInstance.setTitle("OpenNLP English");
				turNLPInstance.setDescription("OpenNLP Production - English");
				turNLPInstance.setTurNLPVendor(turNLPVendorOpenNLP);
				turNLPInstance.setHost(LOCALHOST);
				turNLPInstance.setPort(0);
				turNLPInstance.setLanguage(TurLocaleRepository.EN_US);
				turNLPInstance.setEnabled(1);
				turNLPInstanceRepository.saveAndAssocEntity(turNLPInstance);

				turConfigVar.setId("DEFAULT_NLP");
				turConfigVar.setPath("/nlp");
				turConfigVar.setValue(turNLPInstance.getId());
				turConfigVarRepository.save(turConfigVar);

				turNLPInstance = new TurNLPInstance();
				turNLPInstance.setTitle("OpenNLP Portuguese");
				turNLPInstance.setDescription("OpenNLP Production - Portuguese");
				turNLPInstance.setTurNLPVendor(turNLPVendorOpenNLP);
				turNLPInstance.setHost(LOCALHOST);
				turNLPInstance.setPort(0);
				turNLPInstance.setLanguage(TurLocaleRepository.PT_BR);
				turNLPInstance.setEnabled(1);
				turNLPInstanceRepository.saveAndAssocEntity(turNLPInstance);
			});

			turNLPVendorRepository.findById(TurNLPVendorsConstant.CORENLP).ifPresent(turNLPVendorCoreNLP -> {
				TurNLPInstance turNLPInstance = new TurNLPInstance();
				turNLPInstance.setTitle("CoreNLP");
				turNLPInstance.setDescription("CoreNLP Production - English");
				turNLPInstance.setTurNLPVendor(turNLPVendorCoreNLP);
				turNLPInstance.setHost(LOCALHOST);
				turNLPInstance.setPort(9001);
				turNLPInstance.setLanguage(TurLocaleRepository.EN_US);
				turNLPInstance.setEnabled(1);
				turNLPInstanceRepository.saveAndAssocEntity(turNLPInstance);
			});

			turNLPVendorRepository.findById(TurNLPVendorsConstant.SPACY).ifPresent(turNLPVendorSpaCy -> {
				TurNLPInstance turNLPInstance = new TurNLPInstance();
				turNLPInstance.setTitle("SpaCy");
				turNLPInstance.setDescription("SpaCy Production - English");
				turNLPInstance.setTurNLPVendor(turNLPVendorSpaCy);
				turNLPInstance.setHost(LOCALHOST);
				turNLPInstance.setPort(2800);
				turNLPInstance.setLanguage(TurLocaleRepository.EN_US);
				turNLPInstance.setEnabled(1);
				turNLPInstanceRepository.saveAndAssocEntity(turNLPInstance);
			});

			turNLPVendorRepository.findById(TurNLPVendorsConstant.POLYGLOT).ifPresent(turNLPVendorPolyglot -> {
				TurNLPInstance turNLPInstance = new TurNLPInstance();
				turNLPInstance.setTitle("Polyglot");
				turNLPInstance.setDescription("Polyglot Production - Catalan");
				turNLPInstance.setTurNLPVendor(turNLPVendorPolyglot);
				turNLPInstance.setHost(LOCALHOST);
				turNLPInstance.setPort(2810);
				turNLPInstance.setLanguage(TurLocaleRepository.CA);
				turNLPInstance.setEnabled(1);
				turNLPInstanceRepository.saveAndAssocEntity(turNLPInstance);
			});
		}

	}
}
