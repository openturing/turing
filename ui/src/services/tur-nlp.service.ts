import api from "../commons/http-commons";

class TurNLPDataService {
  getInstances() {
    return api.get("http://localhost:2700/api/nlp");
  }

  getEntities() {
    return api.get("http://localhost:2700/api/entity");
  }
}

export default new TurNLPDataService();
