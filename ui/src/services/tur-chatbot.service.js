import api from "../commons/http-commons";

class TurChatbotDataService {
  getAgents() {
    return api.get("http://localhost:2700/api/converse/agent");
  }
}

export default new TurChatbotDataService();
