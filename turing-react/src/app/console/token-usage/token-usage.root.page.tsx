import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import { IconChartBar } from "@tabler/icons-react";
import { useEffect } from "react";

export default function TokenUsageRootPage() {
    const { pushItem, popItem } = useBreadcrumb();
    useEffect(() => {
        pushItem({ label: "Token Usage", href: ROUTES.TOKEN_USAGE });
        return () => popItem();
    }, []);
    return (
        <Page turIcon={IconChartBar} title="Token Usage" urlBase={ROUTES.TOKEN_USAGE} />
    );
}
