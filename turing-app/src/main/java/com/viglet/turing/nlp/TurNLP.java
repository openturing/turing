/*
 * Copyright (C) 2016-2021 the original author or authors. 
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

package com.viglet.turing.nlp;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;
import org.springframework.web.context.support.WebApplicationContextUtils;

import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.persistence.model.nlp.TurNLPVendor;
import com.viglet.turing.persistence.repository.nlp.TurNLPInstanceRepository;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;
import com.viglet.turing.plugins.nlp.TurNLPImpl;

@ComponentScan
@Component
public class TurNLP {
	private static final Logger logger = LogManager.getLogger(TurNLP.class.getName());
	@Autowired
	private TurNLPInstanceRepository turNLPInstanceRepository;
	@Autowired
	private TurConfigVarRepository turConfigVarRepository;
	@Autowired
	private ServletContext context;
	private Map<String, Object> nlpAttributes;
	private Map<String, Object> attributes = null;
	private TurNLPInstance turNLPInstance = null;
	private TurNLPVendor turNLPVendor = null;

	public void init() {
		turConfigVarRepository.findById("DEFAULT_NLP").ifPresent(turConfigVar -> turNLPInstanceRepository
				.findById(turConfigVar.getValue()).ifPresent(this::init));

	}

	public void init(TurNLPInstance turNLPInstance) {

		this.turNLPInstance = turNLPInstance;
		this.turNLPVendor = turNLPInstance.getTurNLPVendor();
	}

	public void startup() {
		this.init();
	}

	public void startup(TurNLPInstance turNLPInstance, String text) {
		this.init(turNLPInstance);
		Map<String, Object> attribs = new HashMap<>();
		attribs.put("text", text);
		this.setAttributes(attribs);
	}

	public void startup(TurNLPInstance turNLPInstance, Map<String, Object> attributes) {
		this.init(turNLPInstance);
		this.setAttributes(attributes);

	}

	public Map<String, Object> validate() {
		return this.retrieveNLP();
	}

	public Map<String, Object> getAttributes() {
		return attributes;
	}

	public void setAttributes(Map<String, Object> attributes) {
		this.attributes = attributes;
	}

	public Map<String, Object> getNlpAttributes() {
		return nlpAttributes;
	}

	public void setNlpAttributes(Map<String, Object> nlpAttributes) {
		this.nlpAttributes = nlpAttributes;
	}

	public Map<String, Object> retrieveNLP() {
		logger.debug("Executing retrieveNLP...");
		TurNLPImpl nlpService;

		try {
			nlpService = (TurNLPImpl) Class.forName(turNLPVendor.getPlugin()).getDeclaredConstructor().newInstance();
			ApplicationContext applicationContext = WebApplicationContextUtils.getWebApplicationContext(context);
			if (applicationContext != null) {
				applicationContext.getAutowireCapableBeanFactory().autowireBean(nlpService);
				nlpService.startup(turNLPInstance);
				this.setNlpAttributes(nlpService.retrieve(this.getAttributes()));
			}

		} catch (Exception e) {
			logger.error(e);
		}

		if (logger.isDebugEnabled() && this.getNlpAttributes() != null) {
			logger.debug("Result retrieveNLP: {}", this.getNlpAttributes());
		}

		return this.getNlpAttributes();
	}

}
