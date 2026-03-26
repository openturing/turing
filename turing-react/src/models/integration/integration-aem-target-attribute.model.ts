import type { TurIntegrationAemSourceAttribute } from "./integration-aem-source-attribute.model";

export type TurIntegrationAemTargetAttribute = {
  id: string;
  name: string;
  sourceAttrs: TurIntegrationAemSourceAttribute[];
};
