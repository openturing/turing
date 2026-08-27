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
package com.viglet.turing.onstartup.system;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.lang3.LocaleUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.model.system.TurLocale;
import com.viglet.turing.persistence.repository.system.TurLocaleRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Transactional
public class TurLocaleOnStartup {
	private final TurLocaleRepository turLocaleRepository;

	public TurLocaleOnStartup(TurLocaleRepository turLocaleRepository) {
		this.turLocaleRepository = turLocaleRepository;
	}

	/**
	 * Seeds the locale catalog, saving only the locales the table does not
	 * already hold.
	 *
	 * <p>This used to be guarded by {@code findAll().isEmpty()}, which made the
	 * whole catalog a fresh-install-only write: a locale added in a later release
	 * reached a database that had never started the application and no other, so
	 * it could not be assigned to an SN Site on any existing installation. T1031
	 * hit exactly that with the search-engine vendors, where {@code CONTENTAI} was
	 * the first row added since the table existed; T1043 removes the same guard
	 * here.
	 *
	 * <p>The existing ids are read <b>once</b> and diffed, rather than asked per
	 * row as the two vendor seeders do: this catalog is 48 rows, and an
	 * {@code existsById} each would be 48 queries on every boot to answer a
	 * question one query answers. A row already present is left exactly as it is,
	 * so a translation an operator corrected is never overwritten.
	 *
	 * @since 2026.3.4
	 */
	public void createDefaultRows() {
		Set<Locale> present = new HashSet<>();
		turLocaleRepository.findAll().forEach(locale -> present.add(locale.getInitials()));

		List<TurLocale> missing = new ArrayList<>();
		for (TurLocale candidate : defaultLocales()) {
			if (!present.contains(candidate.getInitials())) {
				missing.add(candidate);
			}
		}
		if (missing.isEmpty()) {
			return;
		}
		turLocaleRepository.saveAll(missing);
		log.info("Created {} new locale(s).", missing.size());
	}

	/** The shipped locale catalog. Order is the display order the console shows. */
	private static List<TurLocale> defaultLocales() {
		return List.of(
				new TurLocale(LocaleUtils.toLocale("ar"), "العربية - Arabic", "العربية - Árabe"),
				new TurLocale(LocaleUtils.toLocale("bn"), "বাংলা - Bengali", "বাংলা - Bengali"),
				new TurLocale(LocaleUtils.toLocale("ca"), "Català - Catalan", "Català - Catalão"),
				new TurLocale(LocaleUtils.toLocale("cs"), "Čeština - Czech", "Čeština - Tcheco"),
				new TurLocale(LocaleUtils.toLocale("da"), "Dansk - Danish", "Dansk - Dinamarquês"),
				new TurLocale(LocaleUtils.toLocale("de"), "Deutsch - German", "Deutsch - Alemão"),
				new TurLocale(LocaleUtils.toLocale("el"), "Ελληνικά - Greek", "Ελληνικά - Grego"),
				new TurLocale(Locale.ENGLISH, "English", "English - Inglês"),
				new TurLocale(Locale.US, "English - American English", "English - Inglês Americano"),
				new TurLocale(Locale.UK, "English UK - British English", "English UK - Inglês britânico"),
				new TurLocale(LocaleUtils.toLocale("es"), "Español - Spanish", "Español - Espanhol"),
				new TurLocale(LocaleUtils.toLocale("eu"), "Euskara - Basque", "Euskara - Basco"),
				new TurLocale(LocaleUtils.toLocale("fa"), "فارسی - Persian", "فارسی - Persa"),
				new TurLocale(LocaleUtils.toLocale("fi"), "Suomi - Finnish", "Suomi - Finlandês"),
				new TurLocale(LocaleUtils.toLocale("fil"), "Filipino", "Filipino"),
				new TurLocale(Locale.FRENCH, "Français - French", "Français - Francês"),
				new TurLocale(LocaleUtils.toLocale("ga"), "Gaeilge - Irish", "Gaeilge - Irlandês"),
				new TurLocale(LocaleUtils.toLocale("gl"), "Galego - Galician", "Galego"),
				new TurLocale(LocaleUtils.toLocale("gu"), "ગુજરાતી - Gujarati", "ગુજરાતી - Guzerate"),
				new TurLocale(LocaleUtils.toLocale("he"), "עִבְרִית - Hebrew", "עִבְרִית - Hebraico"),
				new TurLocale(LocaleUtils.toLocale("hi"), "िन्दी - Hindi", "िन्दी - Híndi"),
				new TurLocale(LocaleUtils.toLocale("hu"), "Magyar - Hungarian", "Magyar - Húngaro"),
				new TurLocale(LocaleUtils.toLocale("id"), "Bahasa Indonesia - Indonesian", "Bahasa Indonesia - Indonésio"),
				new TurLocale(LocaleUtils.toLocale("it"), "Italiano - Italian", "Italiano"),
				new TurLocale(LocaleUtils.toLocale("ja"), "日本語 - Japanese", "日本語 - Japonês"),
				new TurLocale(LocaleUtils.toLocale("kn"), "ಕನ್ನಡ - Kannada", "ಕನ್ನಡ - Canarês"),
				new TurLocale(LocaleUtils.toLocale("ko"), "한국어- Korean", "한국어- Coreano"),
				new TurLocale(LocaleUtils.toLocale("mr"), "मराठी - Marathi", "मराठी - Marata"),
				new TurLocale(LocaleUtils.toLocale("msa"), "Bahasa Melayu - Malay", "Bahasa Melayu - Malaio"),
				new TurLocale(LocaleUtils.toLocale("nl"), "Nederlands - Dutch", "Nederlands - Holandês"),
				new TurLocale(LocaleUtils.toLocale("no"), "Norsk - Norwegian", "Norsk - Norueguês"),
				new TurLocale(LocaleUtils.toLocale("pl"), "Polski - Polish", "Polski - Polonês"),
				new TurLocale(LocaleUtils.toLocale("pt"), "Português - Portuguese", "Português"),
				new TurLocale(LocaleUtils.toLocale("pt_PT"), "Português - Portuguese (Portugal)", "Português (Portugal)"),
				new TurLocale(LocaleUtils.toLocale(TurLocaleRepository.PT_BR), "Português - Portuguese (Brazil)", "Português (Brasil)"),
				new TurLocale(LocaleUtils.toLocale("ro"), "Română - Romanian", "Română - Romeno"),
				new TurLocale(LocaleUtils.toLocale("ru"), "Русский - Russian", "Русский - Russo"),
				new TurLocale(LocaleUtils.toLocale("sv"), "Svenska - Swedish", "Svenska - Sueco"),
				new TurLocale(LocaleUtils.toLocale("ta"), "தமிழ் - Tamil", "தமிழ் - Tâmil"),
				new TurLocale(LocaleUtils.toLocale("th"), "ภาษาไทย - Thai", "ภาษาไทย - Tailandês"),
				new TurLocale(LocaleUtils.toLocale("tr"), "Türkçe - Turkish", "Türkçe - Turco"),
				new TurLocale(LocaleUtils.toLocale("uk"), "Українська мова - Ukrainian", "Українська мова - Ucraniano"),
				new TurLocale(LocaleUtils.toLocale("ur"), "اردو - Urdu", "اردو - Urdu"),
				new TurLocale(LocaleUtils.toLocale("vi"), "Tiếng Việt - Vietnamese", "Tiếng Việt - Vietnamita"),
				new TurLocale(LocaleUtils.toLocale("xx_LC"), "LOLCATZ - Lolcat", "LOLCATZ - Lolcat"),
				new TurLocale(LocaleUtils.toLocale("zh"), "Chinese", "Chinês"),
				new TurLocale(LocaleUtils.toLocale("zh_CN"), "简体中文 - Simplified Chinese", "简体中文 - Chinês simplificado"),
				new TurLocale(LocaleUtils.toLocale("zh_TW"), "简体中文 - Traditional Chinese", "繁體中文 - Chinês tradicional"));
	}
}
