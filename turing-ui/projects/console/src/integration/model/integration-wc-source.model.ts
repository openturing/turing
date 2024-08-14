import {TurIntegrationWcExtension} from "./integration-wc-extension.model";
import {TurIntegrationWcUrl} from "./integration-wc-url.model";
import {TurIntegrationWcAttrib} from "./integration-wc-attrib.model";

export interface TurIntegrationWcSource {
  id: string;
  title: string;
  description: string;
  locale: string;
  localeClass: string;
  url: string;
  turSNSite: string;
  username: string;
  password: string;
  allowUrls: TurIntegrationWcUrl[];
  notAllowUrls: TurIntegrationWcUrl[];
  notAllowExtensions: TurIntegrationWcExtension[];
  attributeMappings: TurIntegrationWcAttrib[];
}
