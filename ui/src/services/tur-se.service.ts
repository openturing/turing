import api from "../commons/http-commons";

class TurSEDataService {
  getInstances() {
    return api.get("http://localhost:2700/api/se");
  }
}

export default new TurSEDataService();
