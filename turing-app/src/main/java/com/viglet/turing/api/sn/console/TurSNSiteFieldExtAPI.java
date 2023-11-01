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

package com.viglet.turing.api.sn.console;

import com.google.inject.Inject;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.nlp.TurNLPEntity;
import com.viglet.turing.persistence.model.nlp.TurNLPVendor;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteField;
import com.viglet.turing.persistence.model.sn.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.nlp.TurNLPEntityRepository;
import com.viglet.turing.persistence.repository.nlp.TurNLPVendorEntityRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteFieldRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.sn.TurSNFieldType;
import com.viglet.turing.sn.template.TurSNTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONObject;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/sn/{snSiteId}/field/ext")
@Tag(name = "Semantic Navigation Field Ext", description = "Semantic Navigation Field Ext API")
public class TurSNSiteFieldExtAPI {
	private final TurSNSiteRepository turSNSiteRepository;
	private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
	private final TurSNSiteFieldRepository turSNSiteFieldRepository;
	private final TurNLPEntityRepository turNLPEntityRepository;
	private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
	private final TurNLPVendorEntityRepository turNLPVendorEntityRepository;
	private final TurSNTemplate turSNTemplate;

	@Inject
	public TurSNSiteFieldExtAPI(TurSNSiteRepository turSNSiteRepository,
								TurSNSiteFieldExtRepository turSNSiteFieldExtRepository,
								TurSNSiteFieldRepository turSNSiteFieldRepository,
								TurNLPEntityRepository turNLPEntityRepository,
								TurSNSiteLocaleRepository turSNSiteLocaleRepository,
								TurNLPVendorEntityRepository turNLPVendorEntityRepository,
								TurSNTemplate turSNTemplate) {
		this.turSNSiteRepository = turSNSiteRepository;
		this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
		this.turSNSiteFieldRepository = turSNSiteFieldRepository;
		this.turNLPEntityRepository = turNLPEntityRepository;
		this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
		this.turNLPVendorEntityRepository = turNLPVendorEntityRepository;
		this.turSNTemplate = turSNTemplate;
	}

	@Operation(summary = "Semantic Navigation Site Field Ext List")
	@Transactional
	@GetMapping
	public List<TurSNSiteFieldExt> turSNSiteFieldExtList(@PathVariable String snSiteId) {
		return turSNSiteRepository.findById(snSiteId).map(turSNSite -> {
			Map<String, TurNLPEntity> nerMap = new HashMap<>();
			if (turSNSite.getTurNLPVendor() != null) {
				nerMap = createNERMap(turSNSite.getTurNLPVendor());
			} else {
				turSNSiteFieldExtRepository.deleteByTurSNSiteAndSnType(turSNSite, TurSNFieldType.NER);
			}
			List<TurNLPEntity> turNLPEntityThesaurus = turNLPEntityRepository.findByLocal(1);
			
			Map<String, TurSNSiteField> fieldMap = createFieldMap(turSNSite);

			Map<String, TurNLPEntity> thesaurusMap = createThesaurusMap(turNLPEntityThesaurus);

			List<TurSNSiteFieldExt> turSNSiteFieldExts = this.turSNSiteFieldExtRepository.findByTurSNSite(turSNSite);

			removeDuplicatedFields(fieldMap, nerMap, thesaurusMap, turSNSiteFieldExts);

			for (TurSNSiteField turSNSiteField : fieldMap.values()) {
				TurSNSiteFieldExt turSNSiteFieldExt = saveSNSiteFieldExt(turSNSite, turSNSiteField);

				turSNSiteFieldExts.add(turSNSiteFieldExt);
			}

			nerMap.values().forEach(turNLPEntity -> addTurSNSiteFieldExt(TurSNFieldType.NER, turSNSite,
					turSNSiteFieldExts, turNLPEntity));
			thesaurusMap.values().forEach(turNLPEntity -> addTurSNSiteFieldExt(TurSNFieldType.THESAURUS, turSNSite,
					turSNSiteFieldExts, turNLPEntity));

			return turSNSiteFieldExtRepository.findByTurSNSite(turSNSite);

		}).orElse(new ArrayList<>());

	}

	private Map<String, TurNLPEntity> createNERMap(TurNLPVendor turNLPVendor) {
		Map<String, TurNLPEntity> nerMap = new HashMap<>();
		turNLPVendorEntityRepository.findByTurNLPVendor(turNLPVendor).forEach(turNLPVendorEntity -> {
			TurNLPEntity turNLPEntity = turNLPVendorEntity.getTurNLPEntity();
			nerMap.put(turNLPEntity.getInternalName(), turNLPEntity);
		});
		
		return nerMap;
	}

	private Map<String, TurNLPEntity> createThesaurusMap(List<TurNLPEntity> turNLPEntityThesaurus) {
		Map<String, TurNLPEntity> thesaurusMap = new HashMap<>();
		turNLPEntityThesaurus.forEach(turNLPEntityThesaurusSingle -> thesaurusMap
				.put(turNLPEntityThesaurusSingle.getInternalName(), turNLPEntityThesaurusSingle));
		return thesaurusMap;
	}

	private Map<String, TurSNSiteField> createFieldMap(TurSNSite turSNSite) {
		List<TurSNSiteField> turSNSiteFields = turSNSiteFieldRepository.findByTurSNSite(turSNSite);
		Map<String, TurSNSiteField> fieldMap = new HashMap<>();
		if (turSNSiteFields.isEmpty()) {
			turSNTemplate.createSEFields(turSNSite);
		} else {
			turSNSiteFields.forEach(turSNSiteField -> fieldMap.put(turSNSiteField.getId(), turSNSiteField));
		}
		return fieldMap;
	}

	private void removeDuplicatedFields(Map<String, TurSNSiteField> fieldMap, Map<String, TurNLPEntity> nerMap,
			Map<String, TurNLPEntity> thesaurusMap, List<TurSNSiteFieldExt> turSNSiteFieldExts) {
		for (TurSNSiteFieldExt turSNSiteFieldExt : turSNSiteFieldExts) {
			switch (turSNSiteFieldExt.getSnType()) {
			case SE:
				fieldMap.remove(turSNSiteFieldExt.getExternalId());
				break;
			case NER:
				nerMap.remove(turSNSiteFieldExt.getExternalId());
				break;
			case THESAURUS:
				thesaurusMap.remove(turSNSiteFieldExt.getExternalId());
				break;
			}
		}
	}

	private TurSNSiteFieldExt saveSNSiteFieldExt(TurSNSite turSNSite, TurSNSiteField turSNSiteField) {
		TurSNSiteFieldExt turSNSiteFieldExt = new TurSNSiteFieldExt();
		turSNSiteFieldExt.setEnabled(0);
		turSNSiteFieldExt.setName(turSNSiteField.getName());
		turSNSiteFieldExt.setDescription(turSNSiteField.getDescription());
		turSNSiteFieldExt.setFacet(0);
		turSNSiteFieldExt.setFacetName(turSNSiteField.getName());
		turSNSiteFieldExt.setHl(0);
		turSNSiteFieldExt.setMultiValued(turSNSiteField.getMultiValued());
		turSNSiteFieldExt.setMlt(0);
		turSNSiteFieldExt.setExternalId(turSNSiteField.getId());
		turSNSiteFieldExt.setSnType(TurSNFieldType.SE);
		turSNSiteFieldExt.setType(turSNSiteField.getType());
		turSNSiteFieldExt.setTurSNSite(turSNSite);
		turSNSiteFieldExtRepository.save(turSNSiteFieldExt);
		return turSNSiteFieldExt;
	}

	private void addTurSNSiteFieldExt(TurSNFieldType turSNFieldType, TurSNSite turSNSite,
			List<TurSNSiteFieldExt> turSNSiteFieldExts, TurNLPEntity turNLPEntity) {
		TurSNSiteFieldExt turSNSiteFieldExt = new TurSNSiteFieldExt();
		turSNSiteFieldExt.setEnabled(0);
		turSNSiteFieldExt.setName(turNLPEntity.getInternalName());
		turSNSiteFieldExt.setDescription(turNLPEntity.getDescription());
		turSNSiteFieldExt.setFacet(0);
		turSNSiteFieldExt.setFacetName(turNLPEntity.getName());
		turSNSiteFieldExt.setHl(0);
		turSNSiteFieldExt.setMultiValued(1);
		turSNSiteFieldExt.setMlt(0);
		turSNSiteFieldExt.setExternalId(turNLPEntity.getInternalName());
		turSNSiteFieldExt.setSnType(turSNFieldType);
		turSNSiteFieldExt.setType(TurSEFieldType.STRING);
		turSNSiteFieldExt.setTurSNSite(turSNSite);
		turSNSiteFieldExtRepository.save(turSNSiteFieldExt);

		turSNSiteFieldExts.add(turSNSiteFieldExt);
	}

	@Operation(summary = "Show a Semantic Navigation Site Field Ext")
	@GetMapping("/{id}")
	public TurSNSiteFieldExt turSNSiteFieldExtGet(@PathVariable String snSiteId, @PathVariable String id) {
		return turSNSiteFieldExtRepository.findById(id).orElse(new TurSNSiteFieldExt());
	}

	@Operation(summary = "Update a Semantic Navigation Site Field Ext")
	@PutMapping("/{id}")
	public TurSNSiteFieldExt turSNSiteFieldExtUpdate(@PathVariable String snSiteId, @PathVariable String id,
			@RequestBody TurSNSiteFieldExt turSNSiteFieldExt) {
		return this.turSNSiteFieldExtRepository.findById(id).map(turSNSiteFieldExtEdit -> {
			turSNSiteFieldExtEdit.setFacetName(turSNSiteFieldExt.getFacetName());
			turSNSiteFieldExtEdit.setMultiValued(turSNSiteFieldExt.getMultiValued());
			turSNSiteFieldExtEdit.setName(turSNSiteFieldExt.getName());
			turSNSiteFieldExtEdit.setDescription(turSNSiteFieldExt.getDescription());
			turSNSiteFieldExtEdit.setType(turSNSiteFieldExt.getType());
			turSNSiteFieldExtEdit.setFacet(turSNSiteFieldExt.getFacet());
			turSNSiteFieldExtEdit.setHl(turSNSiteFieldExt.getHl());
			turSNSiteFieldExtEdit.setEnabled(turSNSiteFieldExt.getEnabled());
			turSNSiteFieldExtEdit.setMlt(turSNSiteFieldExt.getMlt());
			turSNSiteFieldExtEdit.setExternalId(turSNSiteFieldExt.getExternalId());
			turSNSiteFieldExtEdit.setRequired(turSNSiteFieldExt.getRequired());
			turSNSiteFieldExtEdit.setDefaultValue(turSNSiteFieldExt.getDefaultValue());
			turSNSiteFieldExtEdit.setNlp(turSNSiteFieldExt.getNlp());
			turSNSiteFieldExtEdit.setSnType(turSNSiteFieldExt.getSnType());
			this.turSNSiteFieldExtRepository.save(turSNSiteFieldExtEdit);

			this.updateExternalField(turSNSiteFieldExt);
		//	TurSolrUtils.updateField(turSNSiteFieldExt.getTurSNSite().getTurSEInstance(),
		//			null, null, null, false);
			return turSNSiteFieldExtEdit;
		}).orElse(new TurSNSiteFieldExt());

	}

	@Transactional
	@Operation(summary = "Delete a Semantic Navigation Site Field Ext")
	@DeleteMapping("/{id}")
	public boolean turSNSiteFieldExtDelete(@PathVariable String snSiteId, @PathVariable String id) {
		return this.turSNSiteFieldExtRepository.findById(id).map(turSNSiteFieldExtEdit -> {
			if (turSNSiteFieldExtEdit.getSnType().equals(TurSNFieldType.SE)) {
				this.turSNSiteFieldRepository.delete(turSNSiteFieldExtEdit.getExternalId());
			}
			this.turSNSiteFieldExtRepository.delete(id);
			return true;
		}).orElse(false);
	}

	@Operation(summary = "Create a Semantic Navigation Site Field Ext")
	@PostMapping
	public TurSNSiteFieldExt turSNSiteFieldExtAdd(@PathVariable String snSiteId,
			@RequestBody TurSNSiteFieldExt turSNSiteFieldExt) {
		return createSEField(snSiteId, turSNSiteFieldExt);
	}

	private TurSNSiteFieldExt createSEField(String snSiteId, TurSNSiteFieldExt turSNSiteFieldExt) {
		return turSNSiteRepository.findById(snSiteId).map(turSNSite -> {
			TurSNSiteField turSNSiteField = new TurSNSiteField();
			turSNSiteField.setDescription(turSNSiteFieldExt.getDescription());
			turSNSiteField.setMultiValued(turSNSiteFieldExt.getMultiValued());
			turSNSiteField.setName(turSNSiteFieldExt.getName());
			turSNSiteField.setType(turSNSiteFieldExt.getType());
			turSNSiteField.setTurSNSite(turSNSite);
			this.turSNSiteFieldRepository.save(turSNSiteField);

			turSNSiteFieldExt.setTurSNSite(turSNSite);
			turSNSiteFieldExt.setSnType(TurSNFieldType.SE);
			turSNSiteFieldExt.setExternalId(turSNSiteField.getId());

			this.turSNSiteFieldExtRepository.save(turSNSiteFieldExt);

			return turSNSiteFieldExt;

		}).orElse(new TurSNSiteFieldExt());

	}

	public void updateExternalField(TurSNSiteFieldExt turSNSiteFieldExt) {
		switch (turSNSiteFieldExt.getSnType()) {
		case SE:
			turSNSiteFieldRepository.findById(turSNSiteFieldExt.getExternalId()).ifPresent(turSNSiteField -> {
				turSNSiteField.setDescription(turSNSiteFieldExt.getDescription());
				turSNSiteField.setMultiValued(turSNSiteFieldExt.getMultiValued());
				turSNSiteField.setName(turSNSiteFieldExt.getName());
				turSNSiteField.setType(turSNSiteFieldExt.getType());
				this.turSNSiteFieldRepository.save(turSNSiteField);
			});

			break;

		case NER, THESAURUS:
			turNLPEntityRepository.findById(turSNSiteFieldExt.getExternalId()).ifPresent(turNLPEntityNER -> {
				turNLPEntityNER.setDescription(turSNSiteFieldExt.getDescription());
				turNLPEntityNER.setInternalName(turSNSiteFieldExt.getName());
				this.turNLPEntityRepository.save(turNLPEntityNER);
			});

			break;

        }
	}

	@GetMapping("/create")
	public List<TurSNSite> turSNSiteFieldExtCreate(@PathVariable String snSiteId, @PathVariable String locale) {
		return turSNSiteRepository.findById(snSiteId).map(turSNSite -> {
			List<TurSNSiteFieldExt> turSNSiteFieldExts = turSNSiteFieldExtRepository
					.findByTurSNSiteAndEnabled(turSNSite, 1);
			turSNSiteFieldExts.forEach(turSNSiteFieldExt -> this.createField(turSNSite, locale, turSNSiteFieldExt));
			return this.turSNSiteRepository.findAll();
		}).orElse(new ArrayList<>());
	}

	public void createField(TurSNSite turSNSite, String locale, TurSNSiteFieldExt turSNSiteFieldExts) {
		TurSNSiteLocale turSNSiteLocale = turSNSiteLocaleRepository.findByTurSNSiteAndLanguage(turSNSite, locale);
		JSONObject jsonAddField = new JSONObject();
		String fieldName;

		if (turSNSiteFieldExts.getSnType() == TurSNFieldType.NER) {
			fieldName = String.format("turing_entity_%s", turSNSiteFieldExts.getName());
		} else {
			fieldName = turSNSiteFieldExts.getName();
		}

		jsonAddField.put("name", fieldName);

		jsonAddField.put("indexed", true);
		jsonAddField.put("stored", true);
		if (turSNSiteFieldExts.getMultiValued() == 1) {
			jsonAddField.put("type", "string");
			jsonAddField.put("multiValued", true);
		} else {
			if (turSNSiteFieldExts.getType().equals(TurSEFieldType.DATE)) {
				jsonAddField.put("type", "pdate");
			} else {
				jsonAddField.put("type", "text_general");
			}
			jsonAddField.put("multiValued", false);
		}
		JSONObject json = new JSONObject();
		json.put("add-field", jsonAddField);
		HttpPost httpPost = new HttpPost(
				String.format("http://%s:%d/solr/%s/schema", turSNSite.getTurSEInstance().getHost(),
						turSNSite.getTurSEInstance().getPort(), turSNSiteLocale.getCore()));
		executeHttpPost(json, httpPost);
		this.copyField(turSNSiteLocale, fieldName, "_text_");
	}

	public void copyField(TurSNSiteLocale turSNSiteLocale, String field, String dest) {

		JSONObject jsonAddField = new JSONObject();
		jsonAddField.put("source", field);

		jsonAddField.put("dest", dest);
		JSONObject json = new JSONObject();
		json.put("add-copy-field", jsonAddField);
		TurSEInstance turSEInstance = turSNSiteLocale.getTurSNSite().getTurSEInstance();
		HttpPost httpPost = new HttpPost(String.format("http://%s:%d/solr/%s/schema", turSEInstance.getHost(),
				turSEInstance.getPort(), turSNSiteLocale.getCore()));
		executeHttpPost(json, httpPost);
	}

	private void executeHttpPost(JSONObject json, HttpPost httpPost) {
		try {
			StringEntity entity = new StringEntity(json.toString());
			httpPost.setEntity(entity);
			httpPost.setHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
			httpPost.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
			try (CloseableHttpClient client = HttpClients.createDefault()) {
				client.execute(httpPost);
			}
		} catch (IOException e) {
			log.error(e.getMessage(), e);
		}
	}
}