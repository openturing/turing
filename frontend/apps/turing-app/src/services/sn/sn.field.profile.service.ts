import type {
  TurProfileFieldConfigRequest,
  TurSNSiteFieldProfileConfig,
} from "@/models/profile/profile-field-config.model";
import axios from "axios";

/**
 * Per-profile facet configuration of a site's fields (spec 003). The "Campos"
 * page selects a global profile and edits, per field, the facet settings that
 * are specific to that profile (or inherit the global value). The default
 * profile writes straight to the global field configuration (handled backend
 * side); non-default profiles store overrides.
 */
export class TurSNFieldProfileService {
  async query(
    siteId: string,
    profileId: string,
  ): Promise<TurSNSiteFieldProfileConfig[]> {
    const response = await axios.get<TurSNSiteFieldProfileConfig[]>(
      `/sn/${siteId}/field/profile/${profileId}`,
    );
    return response.data;
  }

  async saveOverride(
    siteId: string,
    profileId: string,
    fieldExtId: string,
    request: TurProfileFieldConfigRequest,
  ): Promise<TurSNSiteFieldProfileConfig> {
    const response = await axios.put<TurSNSiteFieldProfileConfig>(
      `/sn/${siteId}/field/profile/${profileId}/${fieldExtId}`,
      request,
    );
    return response.data;
  }

  async clearOverride(
    siteId: string,
    profileId: string,
    fieldExtId: string,
  ): Promise<boolean> {
    const response = await axios.delete<boolean>(
      `/sn/${siteId}/field/profile/${profileId}/${fieldExtId}`,
    );
    return response.data;
  }
}
