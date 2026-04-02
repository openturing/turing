import { SubPageHeader } from "@/components/sub.page.header";
import { AiSummaryPanel } from "@/components/ai-summary-panel";
import { IconSparkles } from "@tabler/icons-react";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

export default function SNSiteSummaryPage() {
    const { id } = useParams() as { id: string };
    const { t } = useTranslation();
    useSubPageBreadcrumb(t("sn.insights.title"));

    return (
        <>
            <SubPageHeader
                icon={IconSparkles}
                name={t("sn.insights.title")}
                feature={t("sn.insights.title")}
                description={t("sn.insights.description")}
            />
            <AiSummaryPanel
                endpoint={`/sn/${id}/summary`}
                i18nPrefix="sn.insights"
            />
        </>
    );
}
