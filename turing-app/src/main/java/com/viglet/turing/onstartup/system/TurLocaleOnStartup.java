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

import com.google.inject.Inject;
import com.viglet.turing.persistence.model.system.TurLocale;
import com.viglet.turing.persistence.repository.system.TurLocaleRepository;
import org.apache.commons.lang3.LocaleUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Component
@Transactional
public class TurLocaleOnStartup {
	private final TurLocaleRepository turLocaleRepository;

	@Inject
	public TurLocaleOnStartup(TurLocaleRepository turLocaleRepository) {
		this.turLocaleRepository = turLocaleRepository;
	}

	public void createDefaultRows() {

		if (turLocaleRepository.findAll().isEmpty()) {
			turLocaleRepository.save(new TurLocale(LocaleUtils.toLocale("ar"), "العربية - Arabic", "العربية - Árabe"));
			turLocaleRepository.save(new TurLocale(LocaleUtils.toLocale("bn"), "বাংলা - Bengali", "বাংলা - Bengali"));
			turLocaleRepository.save(new TurLocale(LocaleUtils.toLocale("ca"), "Català - Catalan", "Català - Catalão"));
			turLocaleRepository.save(new TurLocale(LocaleUtils.toLocale("cs"), "Čeština - Czech", "Čeština - Tcheco"));
			turLocaleRepository.save(new TurLocale(LocaleUtils.toLocale("da"), "Dansk - Danish", "Dansk - Dinamarquês"));
			turLocaleRepository.save(new TurLocale(LocaleUtils.toLocale("de"), "Deutsch - German", "Deutsch - Alemão"));
			turLocaleRepository.save(new TurLocale(LocaleUtils.toLocale("el"), "Ελληνικά - Greek", "Ελληνικά - Grego"));
			turLocaleRepository.save(new TurLocale(Locale.ENGLISH, "English", "English - Inglês"));
			turLocaleRepository.save(new TurLocale(Locale.US, "English - American English",
					"English - Inglês Americano"));
			turLocaleRepository.save(new TurLocale(Locale.UK, "English UK - British English",
					"English UK - Inglês britânico"));
			turLocaleRepository.save(new TurLocale(LocaleUtils.toLocale("es"), "Español - Spanish", "Español - Espanhol"));
			turLocaleRepository.save(new TurLocale(LocaleUtils.toLocale("eu"), "Euskara - Basque", "Euskara - Basco"));
			turLocaleRepository.save(new TurLocale(LocaleUtils.toLocale("fa"), "فارسی - Persian", "فارسی - Persa"));
			turLocaleRepository.save(new TurLocale(LocaleUtils.toLocale("fi"), "Suomi - Finnish", "Suomi - Finlandês"));
			turLocaleRepository.save(new TurLocale(LocaleUtils.toLocale("fil"), "Filipino", "Filipino"));
			turLocaleRepository.save(new TurLocale(Locale.FRENCH, "Français - French", "Français - Francês"));
			turLocaleRepository.save(new TurLocale(LocaleUtils.toLocale("ga"), "Gaeilge - Irish", "Gaeilge - Irlandês"));
			turLocaleRepository.save(new TurLocale(LocaleUtils.toLocale("gl"), "Galego - Galician", "Galego"));
			turLocaleRepository.save(new TurLocale(LocaleUtils.toLocale("gu"), "ગુજરાતી - Gujarati", "ગુજરાતી - Guzerate"));
			turLocaleRepository.save(new TurLocale(LocaleUtils.toLocale("he"), "עִבְרִית - Hebrew", "עִבְרִית - Hebraico"));
			turLocaleRepository.save(new TurLocale(LocaleUtils.toLocale("hi"), "िन्दी - Hindi", "िन्दी - Híndi"));
			turLocaleRepository.save(new TurLocale(LocaleUtils.toLocale("hu"), "Magyar - Hungarian", "Magyar - Húngaro"));
			turLocaleRepository
					.save(new TurLocale(LocaleUtils.toLocale("id"), "Bahasa Indonesia - Indonesian", "Bahasa Indonesia - Indonésio"));
			turLocaleRepository.save(new TurLocale(LocaleUtils.toLocale("it"), "Italiano - Italian", "Italiano"));
			turLocaleRepository.save(new TurLocale(LocaleUtils.toLocale("ja"), "日本語 - Japanese", "日本語 - Japonês"));
			turLocaleRepository.save(new TurLocale(LocaleUtils.toLocale("kn"), "ಕನ್ನಡ - Kannada", "ಕನ್ನಡ - Canarês"));
			turLocaleRepository.save(new TurLocale(LocaleUtils.toLocale("ko"), "한국어- Korean", "한국어- Coreano"));
			turLocaleRepository.save(new TurLocale(LocaleUtils.toLocale("mr"), "मराठी - Marathi", "मराठी - Marata"));
			turLocaleRepository.save(new TurLocale(LocaleUtils.toLocale("msa"), "Bahasa Melayu - Malay", "Bahasa Melayu - Malaio"));
			turLocaleRepository.save(new TurLocale(LocaleUtils.toLocale("nl"), "Nederlands - Dutch", "Nederlands - Holandês"));
			turLocaleRepository.save(new TurLocale(LocaleUtils.toLocale("no"), "Norsk - Norwegian", "Norsk - Norueguês"));
			turLocaleRepository.save(new TurLocale(LocaleUtils.toLocale("pl"), "Polski - Polish", "Polski - Polonês"));
			turLocaleRepository.save(new TurLocale(LocaleUtils.toLocale("pt"), "Português - Portuguese", "Português"));
			turLocaleRepository
					.save(new TurLocale(LocaleUtils.toLocale("pt_PT"), "Português - Portuguese (Portugal)", "Português (Portugal)"));
			turLocaleRepository.save(
					new TurLocale(LocaleUtils.toLocale(TurLocaleRepository.PT_BR), "Português - Portuguese (Brazil)", "Português (Brasil)"));
			turLocaleRepository.save(new TurLocale(LocaleUtils.toLocale("ro"), "Română - Romanian", "Română - Romeno"));
			turLocaleRepository.save(new TurLocale(LocaleUtils.toLocale("ru"), "Русский - Russian", "Русский - Russo"));
			turLocaleRepository.save(new TurLocale(LocaleUtils.toLocale("sv"), "Svenska - Swedish", "Svenska - Sueco"));
			turLocaleRepository.save(new TurLocale(LocaleUtils.toLocale("ta"), "தமிழ் - Tamil", "தமிழ் - Tâmil"));
			turLocaleRepository.save(new TurLocale(LocaleUtils.toLocale("th"), "ภาษาไทย - Thai", "ภาษาไทย - Tailandês"));
			turLocaleRepository.save(new TurLocale(LocaleUtils.toLocale("tr"), "Türkçe - Turkish", "Türkçe - Turco"));
			turLocaleRepository.save(new TurLocale(LocaleUtils.toLocale("uk"), "Українська мова - Ukrainian", "Українська мова - Ucraniano"));
			turLocaleRepository.save(new TurLocale(LocaleUtils.toLocale("ur"), "اردو - Urdu", "اردو - Urdu"));
			turLocaleRepository.save(new TurLocale(LocaleUtils.toLocale("vi"), "Tiếng Việt - Vietnamese", "Tiếng Việt - Vietnamita"));
			turLocaleRepository.save(new TurLocale(LocaleUtils.toLocale("xx_LC"), "LOLCATZ - Lolcat", "LOLCATZ - Lolcat"));
			turLocaleRepository.save(new TurLocale(LocaleUtils.toLocale("zh"), "Chinese", "Chinês"));
			turLocaleRepository.save(new TurLocale(LocaleUtils.toLocale("zh_CN"), "简体中文 - Simplified Chinese", "简体中文 - Chinês simplificado"));
			turLocaleRepository.save(new TurLocale(LocaleUtils.toLocale("zh_TW"), "简体中文 - Traditional Chinese", "繁體中文 - Chinês tradicional"));
		}
	}

}