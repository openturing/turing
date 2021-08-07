import api from "../commons/http-commons";

class ShioSiteDataService {
  getAll() {
    return api.get("/site");
  }
}

export default new ShioSiteDataService();