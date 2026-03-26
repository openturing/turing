import type { TurIntegrationAemAttributeSpec } from "./integration-aem-attribute-spec.model";
import type { TurIntegrationAemLocalePath } from "./integration-aem-locale-path.model";
import type { TurIntegrationAemPluginModel } from "./integration-aem-plugin-model.model";

export type TurIntegrationAemSource = {
  id: string;
  name: string;
  endpoint: string;
  username: string;
  password: string;
  rootPath: string;
  contentType: string;
  subType: string;
  oncePattern: string;
  defaultLocale: string;
  localeClass: string;
  deltaClass: string;
  author: boolean;
  publish: boolean;
  authorSNSite: string;
  publishSNSite: string;
  authorURLPrefix: string;
  publishURLPrefix: string;
  localePaths: TurIntegrationAemLocalePath[];
  attributeSpecifications: TurIntegrationAemAttributeSpec[];
  models: TurIntegrationAemPluginModel[];
};
