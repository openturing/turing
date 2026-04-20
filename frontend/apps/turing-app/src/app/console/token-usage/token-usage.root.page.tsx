import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconChartBar } from "@tabler/icons-react";

export default function TokenUsageRootPage() {
    useSubPageBreadcrumb({ label: "Token Usage", href: ROUTES.TOKEN_USAGE });
    return (
        <Page turIcon={IconChartBar} title="Token Usage" urlBase={ROUTES.TOKEN_USAGE} />
    );
}
