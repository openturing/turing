package com.viglet.turing.listener.onstartup.system;

import com.viglet.turing.persistence.model.system.TurLocale;
import com.viglet.turing.persistence.service.system.TurLocaleService;

public class TurLocaleOnStartup {

	public static void createDefaultRows() {

		TurLocaleService turLocaleService = new TurLocaleService();

		if (turLocaleService.listAll().isEmpty()) {
			
			turLocaleService.save(new TurLocale("ar", "العربية - Arabic", "العربية - Árabe"));
			turLocaleService.save(new TurLocale("bn", "বাংলা - Bengali", "বাংলা - Bengali"));
			turLocaleService.save(new TurLocale("ca", "Català - Catalan (beta)", "Català - Catalão (beta)"));
			turLocaleService.save(new TurLocale("cs", "Čeština - Czech", "Čeština - Tcheco"));
			turLocaleService.save(new TurLocale("da", "Dansk - Danish", "Dansk - Dinamarquês"));
			turLocaleService.save(new TurLocale("de", "Deutsch - German", "Deutsch - Alemão"));
			turLocaleService.save(new TurLocale("el", "Ελληνικά - Greek", "Ελληνικά - Grego"));
			turLocaleService.save(new TurLocale("en", "English", "English - Inglês"));
			turLocaleService
					.save(new TurLocale("en-gb", "English UK - British English", "English UK - Inglês britânico"));
			turLocaleService.save(new TurLocale("es", "Español - Spanish", "Español - Espanhol"));
			turLocaleService.save(new TurLocale("eu", "Euskara - Basque", "Euskara - Basco"));
			turLocaleService.save(new TurLocale("fa", "فارسی - Persian", "فارسی - Persa"));
			turLocaleService.save(new TurLocale("fi", "Suomi - Finnish", "Suomi - Finlandês"));
			turLocaleService.save(new TurLocale("fil", "Filipino", "Filipino"));
			turLocaleService.save(new TurLocale("fr", "Français - French", "Français - Francês"));
			turLocaleService.save(new TurLocale("ga", "Gaeilge - Irish", "Gaeilge - Irlandês"));
			turLocaleService.save(new TurLocale("gl", "Galego - Galician", "Galego"));
			turLocaleService.save(new TurLocale("gu", "ગુજરાતી - Gujarati", "ગુજરાતી - Guzerate"));
			turLocaleService.save(new TurLocale("he", "עִבְרִית - Hebrew", "עִבְרִית - Hebraico"));
			turLocaleService.save(new TurLocale("hi", "िन्दी - Hindi", "िन्दी - Híndi"));
			turLocaleService.save(new TurLocale("hu", "Magyar - Hungarian", "Magyar - Húngaro"));
			turLocaleService.save(new TurLocale("id", "Bahasa Indonesia - Indonesian", "Bahasa Indonesia - Indonésio"));
			turLocaleService.save(new TurLocale("it", "Italiano - Italian", "Italiano"));
			turLocaleService.save(new TurLocale("ja", "日本語 - Japanese", "日本語 - Japonês"));
			turLocaleService.save(new TurLocale("kn", "ಕನ್ನಡ - Kannada", "ಕನ್ನಡ - Canarês"));
			turLocaleService.save(new TurLocale("ko", "한국어- Korean", "한국어- Coreano"));
			turLocaleService.save(new TurLocale("mr", "मराठी - Marathi", "मराठी - Marata"));
			turLocaleService.save(new TurLocale("msa", "Bahasa Melayu - Malay", "Bahasa Melayu - Malaio"));
			turLocaleService.save(new TurLocale("nl", "Nederlands - Dutch", "Nederlands - Holandês"));
			turLocaleService.save(new TurLocale("no", "Norsk - Norwegian", "Norsk - Norueguês"));
			turLocaleService.save(new TurLocale("pl", "Polski - Polish", "Polski - Polonês"));
			turLocaleService.save(new TurLocale("pt-pt", "Português - Portuguese (Portugal)", "Português (Portugal)"));
			turLocaleService.save(new TurLocale("pt-br", "Português - Portuguese (Brazil)", "Português (Brasil)"));
			turLocaleService.save(new TurLocale("ro", "Română - Romanian", "Română - Romeno"));
			turLocaleService.save(new TurLocale("ru", "Русский - Russian", "Русский - Russo"));
			turLocaleService.save(new TurLocale("sv", "Svenska - Swedish", "Svenska - Sueco"));
			turLocaleService.save(new TurLocale("ta", "தமிழ் - Tamil", "தமிழ் - Tâmil"));
			turLocaleService.save(new TurLocale("th", "ภาษาไทย - Thai", "ภาษาไทย - Tailandês"));
			turLocaleService.save(new TurLocale("tr", "Türkçe - Turkish", "Türkçe - Turco"));
			turLocaleService
					.save(new TurLocale("uk", "Українська мова - Ukrainian", "Українська мова - Ucraniano"));
			turLocaleService.save(new TurLocale("ur", "اردو - Urdu", "اردو - Urdu"));
			turLocaleService.save(new TurLocale("vi", "Tiếng Việt - Vietnamese", "Tiếng Việt - Vietnamita"));
			turLocaleService.save(new TurLocale("xx-lc", "LOLCATZ - Lolcat", "LOLCATZ - Lolcat"));
			turLocaleService
					.save(new TurLocale("zh-cn", "简体中文 - Simplified Chinese", "简体中文 - Chinês simplificado"));
			turLocaleService
			.save(new TurLocale("zh-tw", "简体中文 - Traditional Chinese", "繁體中文 - Chinês tradicional"));
		}
	}

}