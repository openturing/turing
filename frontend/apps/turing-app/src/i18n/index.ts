import i18n from "i18next";
import LanguageDetector from "i18next-browser-languagedetector";
import { initReactI18next } from "react-i18next";
import { registerVigTranslations } from "@viglet/viglet-design-system";

// English segments
import enAccount from "./locales/en/account.json";
import enAdmin from "./locales/en/admin.json";
import enAiAgent from "./locales/en/aiAgent.json";
import enAiAuthoring from "./locales/en/aiAuthoring.json";
import enApiToken from "./locales/en/apiToken.json";
import enChat from "./locales/en/chat.json";
import enChatFlow from "./locales/en/chatFlow.json";
import enCapabilityMatrix from "./locales/en/capabilityMatrix.json";
import enCommon from "./locales/en/common.json";
import enCostGovernance from "./locales/en/costGovernance.json";
import enCustomTool from "./locales/en/customTool.json";
import enDashboard from "./locales/en/dashboard.json";
import enDialog from "./locales/en/dialog.json";
import enEmbeddingModel from "./locales/en/embeddingModel.json";
import enEvalStudio from "./locales/en/evalStudio.json";
import enExchange from "./locales/en/exchange.json";
import enForms from "./locales/en/forms.json";
import enGit from "./locales/en/git.json";
import enGateway from "./locales/en/gateway.json";
import enGlobalSettings from "./locales/en/globalSettings.json";
import enGraphql from "./locales/en/graphql.json";
import enHome from "./locales/en/home.json";
import enVoiceKiosk from "./locales/en/voiceKiosk.json";
import enIntegration from "./locales/en/integration.json";
import enLogin from "./locales/en/login.json";
import enSetup from "./locales/en/setup.json";
import enSkill from "./locales/en/skill.json";
import enThesaurus from "./locales/en/thesaurus.json";
import enIntent from "./locales/en/intent.json";
import enAnalyticsIntent from "./locales/en/analyticsIntent.json";
import enLlm from "./locales/en/llm.json";
import enModelAdvisor from "./locales/en/modelAdvisor.json";
import enLogging from "./locales/en/logging.json";
import enMarketplace from "./locales/en/marketplace.json";
import enMcp from "./locales/en/mcp.json";
import enNav from "./locales/en/nav.json";
import enPersona from "./locales/en/persona.json";
import enRoutine from "./locales/en/routine.json";
import enWebhook from "./locales/en/webhook.json";
import enPage from "./locales/en/page.json";
import enSe from "./locales/en/se.json";
import enAnn from "./locales/en/ann.json";
import enSearch from "./locales/en/search.json";
import enSidebar from "./locales/en/sidebar.json";
import enSn from "./locales/en/sn.json";
import enStore from "./locales/en/store.json";
import enSystemInfo from "./locales/en/systemInfo.json";
import enTheme from "./locales/en/theme.json";
import enTokenUsage from "./locales/en/tokenUsage.json";
import enParkedConversations from "./locales/en/parkedConversations.json";
import enBento from "./locales/en/bento.json";

// Portuguese segments
import ptAccount from "./locales/pt/account.json";
import ptAdmin from "./locales/pt/admin.json";
import ptAiAgent from "./locales/pt/aiAgent.json";
import ptAiAuthoring from "./locales/pt/aiAuthoring.json";
import ptApiToken from "./locales/pt/apiToken.json";
import ptChat from "./locales/pt/chat.json";
import ptChatFlow from "./locales/pt/chatFlow.json";
import ptCapabilityMatrix from "./locales/pt/capabilityMatrix.json";
import ptCommon from "./locales/pt/common.json";
import ptCostGovernance from "./locales/pt/costGovernance.json";
import ptCustomTool from "./locales/pt/customTool.json";
import ptDashboard from "./locales/pt/dashboard.json";
import ptDialog from "./locales/pt/dialog.json";
import ptEmbeddingModel from "./locales/pt/embeddingModel.json";
import ptEvalStudio from "./locales/pt/evalStudio.json";
import ptExchange from "./locales/pt/exchange.json";
import ptForms from "./locales/pt/forms.json";
import ptGit from "./locales/pt/git.json";
import ptGateway from "./locales/pt/gateway.json";
import ptGlobalSettings from "./locales/pt/globalSettings.json";
import ptGraphql from "./locales/pt/graphql.json";
import ptHome from "./locales/pt/home.json";
import ptVoiceKiosk from "./locales/pt/voiceKiosk.json";
import ptIntegration from "./locales/pt/integration.json";
import ptLogin from "./locales/pt/login.json";
import ptSetup from "./locales/pt/setup.json";
import ptSkill from "./locales/pt/skill.json";
import ptThesaurus from "./locales/pt/thesaurus.json";
import ptIntent from "./locales/pt/intent.json";
import ptAnalyticsIntent from "./locales/pt/analyticsIntent.json";
import ptLlm from "./locales/pt/llm.json";
import ptModelAdvisor from "./locales/pt/modelAdvisor.json";
import ptLogging from "./locales/pt/logging.json";
import ptMarketplace from "./locales/pt/marketplace.json";
import ptMcp from "./locales/pt/mcp.json";
import ptNav from "./locales/pt/nav.json";
import ptPersona from "./locales/pt/persona.json";
import ptRoutine from "./locales/pt/routine.json";
import ptWebhook from "./locales/pt/webhook.json";
import ptPage from "./locales/pt/page.json";
import ptSe from "./locales/pt/se.json";
import ptAnn from "./locales/pt/ann.json";
import ptSearch from "./locales/pt/search.json";
import ptSidebar from "./locales/pt/sidebar.json";
import ptSn from "./locales/pt/sn.json";
import ptStore from "./locales/pt/store.json";
import ptSystemInfo from "./locales/pt/systemInfo.json";
import ptTheme from "./locales/pt/theme.json";
import ptTokenUsage from "./locales/pt/tokenUsage.json";
import ptParkedConversations from "./locales/pt/parkedConversations.json";
import ptBento from "./locales/pt/bento.json";

const en = {
  ...enCommon, ...enTheme, ...enLogin, ...enSetup, ...enDialog, ...enChat, ...enChatFlow,
  ...enSidebar, ...enNav, ...enHome, ...enDashboard, ...enSn, ...enLlm, ...enModelAdvisor, ...enSe,
  ...enStore, ...enIntegration, ...enMcp, ...enCustomTool, ...enEmbeddingModel,
  ...enAiAgent, ...enAiAuthoring, ...enIntent, ...enAnalyticsIntent, ...enApiToken, ...enAdmin, ...enAccount,
  ...enExchange, ...enGraphql, ...enLogging, ...enTokenUsage, ...enParkedConversations, ...enCostGovernance,
  ...enSystemInfo, ...enGlobalSettings, ...enForms, ...enSearch, ...enAnn, ...enCapabilityMatrix,
  ...enPage, ...enGit, ...enGateway, ...enMarketplace, ...enPersona, ...enRoutine, ...enWebhook,
  ...enVoiceKiosk, ...enBento, ...enEvalStudio, ...enSkill, ...enThesaurus,
};

const pt = {
  ...ptCommon, ...ptTheme, ...ptLogin, ...ptSetup, ...ptDialog, ...ptChat, ...ptChatFlow,
  ...ptSidebar, ...ptNav, ...ptHome, ...ptDashboard, ...ptSn, ...ptLlm, ...ptModelAdvisor, ...ptSe,
  ...ptStore, ...ptIntegration, ...ptMcp, ...ptCustomTool, ...ptEmbeddingModel,
  ...ptAiAgent, ...ptAiAuthoring, ...ptIntent, ...ptAnalyticsIntent, ...ptApiToken, ...ptAdmin, ...ptAccount,
  ...ptExchange, ...ptGraphql, ...ptLogging, ...ptTokenUsage, ...ptParkedConversations, ...ptCostGovernance,
  ...ptSystemInfo, ...ptGlobalSettings, ...ptForms, ...ptSearch, ...ptAnn, ...ptCapabilityMatrix,
  ...ptPage, ...ptGit, ...ptGateway, ...ptMarketplace, ...ptPersona, ...ptRoutine, ...ptWebhook,
  ...ptVoiceKiosk, ...ptBento, ...ptEvalStudio, ...ptSkill, ...ptThesaurus,
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
      order: ["localStorage", "navigator", "htmlTag", "cookie"],
      caches: ["localStorage"],
    },
  });

registerVigTranslations(i18n);

export default i18n;
