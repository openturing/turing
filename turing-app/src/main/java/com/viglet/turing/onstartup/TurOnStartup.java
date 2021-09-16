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

package com.viglet.turing.onstartup;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.onstartup.auth.TurGroupOnStartup;
import com.viglet.turing.onstartup.auth.TurUserOnStartup;
import com.viglet.turing.onstartup.converse.TurConverseAgentOnStartup;
import com.viglet.turing.onstartup.ml.TurMLInstanceOnStartup;
import com.viglet.turing.onstartup.ml.TurMLVendorOnStartup;
import com.viglet.turing.onstartup.nlp.TurNLPEntityOnStartup;
import com.viglet.turing.onstartup.nlp.TurNLPFeatureOnStartup;
import com.viglet.turing.onstartup.nlp.TurNLPInstanceOnStartup;
import com.viglet.turing.onstartup.nlp.TurNLPVendorEntityOnStartup;
import com.viglet.turing.onstartup.nlp.TurNLPVendorOnStartup;
import com.viglet.turing.onstartup.se.TurSEInstanceOnStartup;
import com.viglet.turing.onstartup.se.TurSEVendorOnStartup;
import com.viglet.turing.onstartup.sn.TurSNSiteOnStartup;
import com.viglet.turing.onstartup.storage.TurDataGroupStartup;
import com.viglet.turing.onstartup.system.TurConfigVarOnStartup;
import com.viglet.turing.onstartup.system.TurLocaleOnStartup;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;

@Component
@Transactional
public class TurOnStartup implements ApplicationRunner {

	@Autowired
	private TurConfigVarRepository turConfigVarRepository;

	@Autowired
	private TurLocaleOnStartup turLocaleOnStartup;
	@Autowired
	private TurNLPVendorOnStartup turNLPVendorOnStartup;
	@Autowired
	private TurNLPEntityOnStartup turNLPEntityOnStartup;
	@Autowired
	private TurNLPVendorEntityOnStartup turNLPVendorEntityOnStartup;
	@Autowired
	private TurNLPFeatureOnStartup turNLPFeatureOnStartup;
	@Autowired
	private TurNLPInstanceOnStartup turNLPInstanceOnStartup;
	@Autowired
	private TurMLVendorOnStartup turMLVendorOnStartup;
	@Autowired
	private TurMLInstanceOnStartup turMLInstanceOnStartup;
	@Autowired
	private TurSEVendorOnStartup turSEVendorOnStartup;
	@Autowired
	private TurSEInstanceOnStartup turSEInstanceOnStartup;
	@Autowired
	private TurDataGroupStartup turDataGroupStartup;
	@Autowired
	private TurConfigVarOnStartup turConfigVarOnStartup;
	@Autowired
	private TurSNSiteOnStartup turSNSiteOnStartup;
	@Autowired
	private TurConverseAgentOnStartup turConverseAgentOnStartup;
	@Autowired
	private TurUserOnStartup turUserOnStartup;
	@Autowired
	private TurGroupOnStartup turGroupOnStartup;
	
	@Override
	public void run(ApplicationArguments arg0) throws Exception {
		final String FIRST_TIME = "FIRST_TIME";

		if (!turConfigVarRepository.findById(FIRST_TIME).isPresent()) {

			System.out.println("First Time Configuration ...");

			turLocaleOnStartup.createDefaultRows();
			turGroupOnStartup.createDefaultRows();
			turUserOnStartup.createDefaultRows();		
			turNLPFeatureOnStartup.createDefaultRows();
			turNLPVendorOnStartup.createDefaultRows();
			turMLVendorOnStartup.createDefaultRows();
			turSEVendorOnStartup.createDefaultRows();

			turNLPEntityOnStartup.createDefaultRows();
			turNLPVendorEntityOnStartup.createDefaultRows();
			turNLPInstanceOnStartup.createDefaultRows();
			turMLInstanceOnStartup.createDefaultRows();
			turSEInstanceOnStartup.createDefaultRows();
			turDataGroupStartup.createDefaultRows();
			turSNSiteOnStartup.createDefaultRows();
			turConverseAgentOnStartup.createDefaultRows();
			
			turConfigVarOnStartup.createDefaultRows();

			System.out.println("Configuration finished.");
		}

	}

}