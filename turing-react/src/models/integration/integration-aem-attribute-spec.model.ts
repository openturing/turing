export type TurIntegrationAemAttributeSpec = {
  id: string;
  className: string;
  text: string;
  name: string;
  type: string;
  mandatory: boolean;
  multiValued: boolean;
  description: string;
  facet: boolean;
  facetNames: Record<string, string>;
};
