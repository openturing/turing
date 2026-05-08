import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconMessageCircle2 } from "@tabler/icons-react";

export default function ChatAnalyticsRootPage() {
  useSubPageBreadcrumb({ label: "Chat Analytics", href: ROUTES.CHAT_ANALYTICS });
  return (
    <Page turIcon={IconMessageCircle2} title="Chat Analytics" urlBase={ROUTES.CHAT_ANALYTICS} />
  );
}
