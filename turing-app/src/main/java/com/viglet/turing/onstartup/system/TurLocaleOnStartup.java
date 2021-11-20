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

			turLocaleRepository.save(new TurLocale("ar", "Ø§Ù„Ø¹Ø±Ø¨ÙŠØ© - Arabic", "Ø§Ù„Ø¹Ø±Ø¨ÙŠØ© - Ã�rabe"));
			turLocaleRepository.save(new TurLocale("bn", "à¦¬à¦¾à¦‚à¦²à¦¾ - Bengali", "à¦¬à¦¾à¦‚à¦²à¦¾ - Bengali"));
			turLocaleRepository.save(new TurLocale("ca", "CatalÃ  - Catalan", "CatalÃ  - CatalÃ£o"));
			turLocaleRepository.save(new TurLocale("cs", "ÄŒeÅ¡tina - Czech", "ÄŒeÅ¡tina - Tcheco"));
			turLocaleRepository.save(new TurLocale("da", "Dansk - Danish", "Dansk - DinamarquÃªs"));
			turLocaleRepository.save(new TurLocale("de", "Deutsch - German", "Deutsch - AlemÃ£o"));
			turLocaleRepository.save(new TurLocale("el", "Î•Î»Î»Î·Î½Î¹ÎºÎ¬ - Greek", "Î•Î»Î»Î·Î½Î¹ÎºÎ¬ - Grego"));
			turLocaleRepository.save(new TurLocale(TurLocaleRepository.EN_US, "English", "English - InglÃªs"));
			turLocaleRepository
					.save(new TurLocale(TurLocaleRepository.EN_GB, "English UK - British English", "English UK - InglÃªs britÃ¢nico"));
			turLocaleRepository.save(new TurLocale("es", "EspaÃ±ol - Spanish", "EspaÃ±ol - Espanhol"));
			turLocaleRepository.save(new TurLocale("eu", "Euskara - Basque", "Euskara - Basco"));
			turLocaleRepository.save(new TurLocale("fa", "Ù�Ø§Ø±Ø³ÛŒ - Persian", "Ù�Ø§Ø±Ø³ÛŒ - Persa"));
			turLocaleRepository.save(new TurLocale("fi", "Suomi - Finnish", "Suomi - FinlandÃªs"));
			turLocaleRepository.save(new TurLocale("fil", "Filipino", "Filipino"));
			turLocaleRepository.save(new TurLocale("fr", "FranÃ§ais - French", "FranÃ§ais - FrancÃªs"));
			turLocaleRepository.save(new TurLocale("ga", "Gaeilge - Irish", "Gaeilge - IrlandÃªs"));
			turLocaleRepository.save(new TurLocale("gl", "Galego - Galician", "Galego"));
			turLocaleRepository.save(new TurLocale("gu", "àª—à«�àªœàª°àª¾àª¤à«€ - Gujarati", "àª—à«�àªœàª°àª¾àª¤à«€ - Guzerate"));
			turLocaleRepository.save(new TurLocale("he", "×¢Ö´×‘Ö°×¨Ö´×™×ª - Hebrew", "×¢Ö´×‘Ö°×¨Ö´×™×ª - Hebraico"));
			turLocaleRepository.save(new TurLocale("hi", "à¤¿à¤¨à¥�à¤¦à¥€ - Hindi", "à¤¿à¤¨à¥�à¤¦à¥€ - HÃ­ndi"));
			turLocaleRepository.save(new TurLocale("hu", "Magyar - Hungarian", "Magyar - HÃºngaro"));
			turLocaleRepository
					.save(new TurLocale("id", "Bahasa Indonesia - Indonesian", "Bahasa Indonesia - IndonÃ©sio"));
			turLocaleRepository.save(new TurLocale("it", "Italiano - Italian", "Italiano"));
			turLocaleRepository.save(new TurLocale("ja", "æ—¥æœ¬èªž - Japanese", "æ—¥æœ¬èªž - JaponÃªs"));
			turLocaleRepository.save(new TurLocale("kn", "à²•à²¨à³�à²¨à²¡ - Kannada", "à²•à²¨à³�à²¨à²¡ - CanarÃªs"));
			turLocaleRepository.save(new TurLocale("ko", "í•œêµ­ì–´- Korean", "í•œêµ­ì–´- Coreano"));
			turLocaleRepository.save(new TurLocale("mr", "à¤®à¤°à¤¾à¤ à¥€ - Marathi", "à¤®à¤°à¤¾à¤ à¥€ - Marata"));
			turLocaleRepository.save(new TurLocale("msa", "Bahasa Melayu - Malay", "Bahasa Melayu - Malaio"));
			turLocaleRepository.save(new TurLocale("nl", "Nederlands - Dutch", "Nederlands - HolandÃªs"));
			turLocaleRepository.save(new TurLocale("no", "Norsk - Norwegian", "Norsk - NorueguÃªs"));
			turLocaleRepository.save(new TurLocale("pl", "Polski - Polish", "Polski - PolonÃªs"));
			turLocaleRepository
					.save(new TurLocale("pt-pt", "PortuguÃªs - Portuguese (Portugal)", "PortuguÃªs (Portugal)"));
			turLocaleRepository.save(new TurLocale(TurLocaleRepository.PT_BR, "PortuguÃªs - Portuguese (Brazil)", "PortuguÃªs (Brasil)"));
			turLocaleRepository.save(new TurLocale("ro", "RomÃ¢nÄƒ - Romanian", "RomÃ¢nÄƒ - Romeno"));
			turLocaleRepository.save(new TurLocale("ru", "Ð ÑƒÑ�Ñ�ÐºÐ¸Ð¹ - Russian", "Ð ÑƒÑ�Ñ�ÐºÐ¸Ð¹ - Russo"));
			turLocaleRepository.save(new TurLocale("sv", "Svenska - Swedish", "Svenska - Sueco"));
			turLocaleRepository.save(new TurLocale("ta", "à®¤à®®à®¿à®´à¯� - Tamil", "à®¤à®®à®¿à®´à¯� - TÃ¢mil"));
			turLocaleRepository.save(new TurLocale("th", "à¸ à¸²à¸©à¸²à¹„à¸—à¸¢ - Thai", "à¸ à¸²à¸©à¸²à¹„à¸—à¸¢ - TailandÃªs"));
			turLocaleRepository.save(new TurLocale("tr", "TÃ¼rkÃ§e - Turkish", "TÃ¼rkÃ§e - Turco"));
			turLocaleRepository.save(new TurLocale("uk", "Ð£ÐºÑ€Ð°Ñ—Ð½Ñ�ÑŒÐºÐ° Ð¼Ð¾Ð²Ð° - Ukrainian", "Ð£ÐºÑ€Ð°Ñ—Ð½Ñ�ÑŒÐºÐ° Ð¼Ð¾Ð²Ð° - Ucraniano"));
			turLocaleRepository.save(new TurLocale("ur", "Ø§Ø±Ø¯Ùˆ - Urdu", "Ø§Ø±Ø¯Ùˆ - Urdu"));
			turLocaleRepository.save(new TurLocale("vi", "Tiáº¿ng Viá»‡t - Vietnamese", "Tiáº¿ng Viá»‡t - Vietnamita"));
			turLocaleRepository.save(new TurLocale("xx-lc", "LOLCATZ - Lolcat", "LOLCATZ - Lolcat"));
			turLocaleRepository.save(new TurLocale("zh-cn", "ç®€ä½“ä¸­æ–‡ - Simplified Chinese", "ç®€ä½“ä¸­æ–‡ - ChinÃªs simplificado"));
			turLocaleRepository.save(new TurLocale("zh-tw", "ç®€ä½“ä¸­æ–‡ - Traditional Chinese", "ç¹�é«”ä¸­æ–‡ - ChinÃªs tradicional"));
		}
	}

}