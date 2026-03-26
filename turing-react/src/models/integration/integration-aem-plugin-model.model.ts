import type { TurIntegrationAemTargetAttribute } from "./integration-aem-target-attribute.model";

export type TurIntegrationAemPluginModel = {
  id: string;
  type: string;
  subType: string;
  className: string;
  targetAttrs: TurIntegrationAemTargetAttribute[];
};
