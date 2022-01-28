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

package com.viglet.turing.onstartup.system;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.model.system.TurLocale;
import com.viglet.turing.persistence.repository.system.TurLocaleRepository;

@Component
@Transactional
public class TurLocaleOnStartup {

	@Autowired
	private TurLocaleRepository turLocaleRepository;

	public void createDefaultRows() {

		if (turLocaleRepository.findAll().isEmpty()) {
			turLocaleRepository.save(new TurLocale("ar", "العربية - Arabic", "العربية - Árabe"));
			turLocaleRepository.save(new TurLocale("bn", "বাংলা - Bengali", "বাংলা - Bengali"));
			turLocaleRepository.save(new TurLocale("ca", "Català - Catalan", "Català - Catalão"));
			turLocaleRepository.save(new TurLocale("cs", "Čeština - Czech", "Čeština - Tcheco"));
			turLocaleRepository.save(new TurLocale("da", "Dansk - Danish", "Dansk - Dinamarquês"));
			turLocaleRepository.save(new TurLocale("de", "Deutsch - German", "Deutsch - Alemão"));
			turLocaleRepository.save(new TurLocale("el", "Ελληνικά - Greek", "Ελληνικά - Grego"));
			turLocaleRepository.save(new TurLocale("en", "English", "English - Inglês"));
			turLocaleRepository.save(new TurLocale(TurLocaleRepository.EN_US, "English - American English",
					"English - Inglês Americano"));
			turLocaleRepository.save(new TurLocale(TurLocaleRepository.EN_GB, "English UK - British English",
					"English UK - Inglês britânico"));
			turLocaleRepository.save(new TurLocale("es", "Español - Spanish", "Español - Espanhol"));
			turLocaleRepository.save(new TurLocale("eu", "Euskara - Basque", "Euskara - Basco"));
			turLocaleRepository.save(new TurLocale("fa", "فارسی - Persian", "فارسی - Persa"));
			turLocaleRepository.save(new TurLocale("fi", "Suomi - Finnish", "Suomi - Finlandês"));
			turLocaleRepository.save(new TurLocale("fil", "Filipino", "Filipino"));
			turLocaleRepository.save(new TurLocale("fr", "Français - French", "Français - Francês"));
			turLocaleRepository.save(new TurLocale("ga", "Gaeilge - Irish", "Gaeilge - Irlandês"));
			turLocaleRepository.save(new TurLocale("gl", "Galego - Galician", "Galego"));
			turLocaleRepository.save(new TurLocale("gu", "ગુજરાતી - Gujarati", "ગુજરાતી - Guzerate"));
			turLocaleRepository.save(new TurLocale("he", "עִבְרִית - Hebrew", "עִבְרִית - Hebraico"));
			turLocaleRepository.save(new TurLocale("hi", "िन्दी - Hindi", "िन्दी - Híndi"));
			turLocaleRepository.save(new TurLocale("hu", "Magyar - Hungarian", "Magyar - Húngaro"));
			turLocaleRepository
					.save(new TurLocale("id", "Bahasa Indonesia - Indonesian", "Bahasa Indonesia - Indonésio"));
			turLocaleRepository.save(new TurLocale("it", "Italiano - Italian", "Italiano"));
			turLocaleRepository.save(new TurLocale("ja", "日本語 - Japanese", "日本語 - Japonês"));
			turLocaleRepository.save(new TurLocale("kn", "ಕನ್ನಡ - Kannada", "ಕನ್ನಡ - Canarês"));
			turLocaleRepository.save(new TurLocale("ko", "한국어- Korean", "한국어- Coreano"));
			turLocaleRepository.save(new TurLocale("mr", "मराठी - Marathi", "मराठी - Marata"));
			turLocaleRepository.save(new TurLocale("msa", "Bahasa Melayu - Malay", "Bahasa Melayu - Malaio"));
			turLocaleRepository.save(new TurLocale("nl", "Nederlands - Dutch", "Nederlands - Holandês"));
			turLocaleRepository.save(new TurLocale("no", "Norsk - Norwegian", "Norsk - Norueguês"));
			turLocaleRepository.save(new TurLocale("pl", "Polski - Polish", "Polski - Polonês"));
			turLocaleRepository.save(new TurLocale("pt", "Português - Portuguese", "Português"));
			turLocaleRepository
					.save(new TurLocale("pt-pt", "Português - Portuguese (Portugal)", "Português (Portugal)"));
			turLocaleRepository.save(
					new TurLocale(TurLocaleRepository.PT_BR, "Português - Portuguese (Brazil)", "Português (Brasil)"));
			turLocaleRepository.save(new TurLocale("ro", "Română - Romanian", "Română - Romeno"));
			turLocaleRepository.save(new TurLocale("ru", "Русский - Russian", "Русский - Russo"));
			turLocaleRepository.save(new TurLocale("sv", "Svenska - Swedish", "Svenska - Sueco"));
			turLocaleRepository.save(new TurLocale("ta", "தமிழ் - Tamil", "தமிழ் - Tâmil"));
			turLocaleRepository.save(new TurLocale("th", "ภาษาไทย - Thai", "ภาษาไทย - Tailandês"));
			turLocaleRepository.save(new TurLocale("tr", "Türkçe - Turkish", "Türkçe - Turco"));
			turLocaleRepository.save(new TurLocale("uk", "Українська мова - Ukrainian", "Українська мова - Ucraniano"));
			turLocaleRepository.save(new TurLocale("ur", "اردو - Urdu", "اردو - Urdu"));
			turLocaleRepository.save(new TurLocale("vi", "Tiếng Việt - Vietnamese", "Tiếng Việt - Vietnamita"));
			turLocaleRepository.save(new TurLocale("xx-lc", "LOLCATZ - Lolcat", "LOLCATZ - Lolcat"));
			turLocaleRepository.save(new TurLocale("zh", "Chinese", "Chinês"));
			turLocaleRepository.save(new TurLocale("zh-cn", "简体中文 - Simplified Chinese", "简体中文 - Chinês simplificado"));
			turLocaleRepository.save(new TurLocale("zh-tw", "简体中文 - Traditional Chinese", "繁體中文 - Chinês tradicional"));
		}
	}

}