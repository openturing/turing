import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconCoin } from "@tabler/icons-react";

export default function CostGovernanceRootPage() {
    useSubPageBreadcrumb({ label: "Cost Governance", href: ROUTES.COST_GOVERNANCE });
    return (
        <Page turIcon={IconCoin} title="Cost Governance" urlBase={ROUTES.COST_GOVERNANCE} />
    );
}
