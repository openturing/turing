/*
 * Copyright (C) 2016-2019 the original author or authors. 
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
import com.viglet.turing.persistence.model.nlp.TurNLPVendor;
import com.viglet.turing.persistence.repository.nlp.TurNLPVendorRepository;

@Component
@Transactional
public class TurNLPVendorOnStartup {

	@Autowired
	private TurNLPVendorRepository turNLPVendorRepository;
	
	public void createDefaultRows() {
		
		if (turNLPVendorRepository.findAll().isEmpty()) {
			
			TurNLPVendor turNLPVendor = new TurNLPVendor();
			turNLPVendor.setId(TurNLPVendorsConstant.CORENLP);
			turNLPVendor.setDescription("Stanford CoreNLP");
			turNLPVendor.setPlugin("com.viglet.turing.plugins.corenlp.TurCoreNLPConnector");
			turNLPVendor.setTitle("Stanford CoreNLP");
			turNLPVendor.setWebsite("http://stanfordnlp.github.io/CoreNLP");
			turNLPVendorRepository.save(turNLPVendor);

			turNLPVendor = new TurNLPVendor();
			turNLPVendor.setId(TurNLPVendorsConstant.OTCA);
			turNLPVendor.setDescription("OpenText OTCA");
			turNLPVendor.setPlugin("com.viglet.turing.plugins.otca.TurTMEConnector");
			turNLPVendor.setTitle("OpenText OTCA");
			turNLPVendor.setWebsite("http://opentext.com/what-we-do/products/discovery");
			turNLPVendorRepository.save(turNLPVendor);

			turNLPVendor = new TurNLPVendor();
			turNLPVendor.setId(TurNLPVendorsConstant.OPENNLP);
			turNLPVendor.setDescription("Apache OpenNLP");
			turNLPVendor.setPlugin("com.viglet.turing.plugins.opennlp.TurOpenNLPConnector");
			turNLPVendor.setTitle("Apache OpenNLP");
			turNLPVendor.setWebsite("https://opennlp.apache.org");
			turNLPVendorRepository.save(turNLPVendor);
			
			turNLPVendor = new TurNLPVendor();
			turNLPVendor.setId(TurNLPVendorsConstant.SPACY);
			turNLPVendor.setTitle("SpaCy");
			turNLPVendor.setPlugin("com.viglet.turing.plugins.spacy.TurSpaCyConnector");
			turNLPVendor.setDescription("SpaCy");
			turNLPVendor.setWebsite("https://spacy.io");
			turNLPVendorRepository.save(turNLPVendor);
			
			turNLPVendor = new TurNLPVendor();
			turNLPVendor.setId(TurNLPVendorsConstant.POLYGLOT);
			turNLPVendor.setTitle("Polyglot");
			turNLPVendor.setPlugin("com.viglet.turing.plugins.polyglot.TurPolyglotConnector");
			turNLPVendor.setDescription("Polyglot");
			turNLPVendor.setWebsite("http://polyglot-nlp.com");
			turNLPVendorRepository.save(turNLPVendor);
		}
	}

}
