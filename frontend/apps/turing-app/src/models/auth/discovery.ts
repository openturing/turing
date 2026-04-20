export type TurDiscoveryAPI = {
  product: string;
  keycloak: boolean;
  multiTenant: boolean;
  authThirdparty: boolean;
  selfRegistration: boolean;
  oauth2Providers: string[];
};
