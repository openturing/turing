import GlobalSettingsPage from "@/app/console/system/global-settings.page";

/**
 * Bento global settings — T566. The platform-formatting + email-provider
 * settings form already carries its own sticky header (title + save) and pulls
 * no console SidebarTrigger, so hosting it in the Bento shell is a direct reuse
 * of {@link GlobalSettingsPage}; both edit the same TurGlobalSettings record.
 */
export default function BentoGlobalSettingsPage() {
  return <GlobalSettingsPage />;
}
