export interface TurPageManifest {
  name?: string;
  version?: string;
  author?: string;
  repository?: string;
  description?: string;
  buildDate?: string;
  snSite?: string;
  locale?: string;
  framework?: string;
  buildTool?: string;
}

export interface TurPageSite {
  name: string;
  manifest?: TurPageManifest;
}
