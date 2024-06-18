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
  localePaths: string;
  attributeMappings: string;
}
