import i18n from "i18next";
import LanguageDetector from "i18next-browser-languagedetector";
import { initReactI18next } from "react-i18next";

// English segments
import enAccount from "./locales/en/account.json";
import enAdmin from "./locales/en/admin.json";
import enAiAgent from "./locales/en/aiAgent.json";
import enApiToken from "./locales/en/apiToken.json";
import enChat from "./locales/en/chat.json";
import enCommon from "./locales/en/common.json";
import enDialog from "./locales/en/dialog.json";
import enEmbeddingModel from "./locales/en/embeddingModel.json";
import enExchange from "./locales/en/exchange.json";
import enForms from "./locales/en/forms.json";
import enGlobalSettings from "./locales/en/globalSettings.json";
import enGraphql from "./locales/en/graphql.json";
import enHome from "./locales/en/home.json";
import enIntegration from "./locales/en/integration.json";
import enLogin from "./locales/en/login.json";
import enIntent from "./locales/en/intent.json";
import enLlm from "./locales/en/llm.json";
import enLogging from "./locales/en/logging.json";
import enMcp from "./locales/en/mcp.json";
import enNav from "./locales/en/nav.json";
import enSe from "./locales/en/se.json";
import enSidebar from "./locales/en/sidebar.json";
import enSn from "./locales/en/sn.json";
import enStore from "./locales/en/store.json";
import enSystemInfo from "./locales/en/systemInfo.json";
import enTheme from "./locales/en/theme.json";
import enTokenUsage from "./locales/en/tokenUsage.json";

// Portuguese segments
import ptAccount from "./locales/pt/account.json";
import ptAdmin from "./locales/pt/admin.json";
import ptAiAgent from "./locales/pt/aiAgent.json";
import ptApiToken from "./locales/pt/apiToken.json";
import ptChat from "./locales/pt/chat.json";
import ptCommon from "./locales/pt/common.json";
import ptDialog from "./locales/pt/dialog.json";
import ptEmbeddingModel from "./locales/pt/embeddingModel.json";
import ptExchange from "./locales/pt/exchange.json";
import ptForms from "./locales/pt/forms.json";
import ptGlobalSettings from "./locales/pt/globalSettings.json";
import ptGraphql from "./locales/pt/graphql.json";
import ptHome from "./locales/pt/home.json";
import ptIntegration from "./locales/pt/integration.json";
import ptLogin from "./locales/pt/login.json";
import ptIntent from "./locales/pt/intent.json";
import ptLlm from "./locales/pt/llm.json";
import ptLogging from "./locales/pt/logging.json";
import ptMcp from "./locales/pt/mcp.json";
import ptNav from "./locales/pt/nav.json";
import ptSe from "./locales/pt/se.json";
import ptSidebar from "./locales/pt/sidebar.json";
import ptSn from "./locales/pt/sn.json";
import ptStore from "./locales/pt/store.json";
import ptSystemInfo from "./locales/pt/systemInfo.json";
import ptTheme from "./locales/pt/theme.json";
import ptTokenUsage from "./locales/pt/tokenUsage.json";

const en = {
  ...enCommon, ...enTheme, ...enLogin, ...enDialog, ...enChat,
  ...enSidebar, ...enNav, ...enHome, ...enSn, ...enLlm, ...enSe,
  ...enStore, ...enIntegration, ...enMcp, ...enEmbeddingModel,
  ...enAiAgent, ...enIntent, ...enApiToken, ...enAdmin, ...enAccount,
  ...enExchange, ...enGraphql, ...enLogging, ...enTokenUsage,
  ...enSystemInfo, ...enGlobalSettings, ...enForms,
};

const pt = {
  ...ptCommon, ...ptTheme, ...ptLogin, ...ptDialog, ...ptChat,
  ...ptSidebar, ...ptNav, ...ptHome, ...ptSn, ...ptLlm, ...ptSe,
  ...ptStore, ...ptIntegration, ...ptMcp, ...ptEmbeddingModel,
  ...ptAiAgent, ...ptIntent, ...ptApiToken, ...ptAdmin, ...ptAccount,
  ...ptExchange, ...ptGraphql, ...ptLogging, ...ptTokenUsage,
  ...ptSystemInfo, ...ptGlobalSettings, ...ptForms,
};

/**
 * i18n configuration with browser language detection.
 * Translations are split into per-segment files under locales/en/ and locales/pt/.
 *
 * @since 2026.1.14
 */
i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      en: { translation: en },
      pt: { translation: pt },
    },
    fallbackLng: "en",
    interpolation: {
      escapeValue: false,
    },
    detection: {
      order: ["navigator", "htmlTag", "localStorage", "cookie"],
      caches: ["localStorage"],
    },
  });

export default i18n;
