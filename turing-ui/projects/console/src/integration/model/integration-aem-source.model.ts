import {TurIntegrationAemLocalePath} from "./integration-aem-locale-path.model";

export interface TurIntegrationAemSource {
  id: string;
  url: string;
  username: string;
  password: string;
  rootPath: string;
  contentType: string;
  subType: string;
  turSNSite: string;
  siteName: string;
  defaultLocale: string;
  providerName: string;
  group: string;
  urlPrefix: string;
  oncePattern: string;
  mappingJson: string;
  localePaths: TurIntegrationAemLocalePath[];
  attributeMappings: string;
}
