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
  allowUrls: string[];
  notAllowUrls: string[];
  notAllowExtensions: string[];
  attributeMappings: string[];
}
