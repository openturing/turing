import api from "../commons/http-commons";

class TurSNDataService {
  getSites() {
    return api.get("http://localhost:2700/api/sn");
  }
}

export default new TurSNDataService();
