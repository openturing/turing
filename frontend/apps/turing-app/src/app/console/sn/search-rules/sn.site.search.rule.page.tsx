import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SNSiteSearchRuleForm } from "@/components/sn/search-rule/sn.site.search.rule.form";
import type { TurSNSiteSearchRule } from "@/models/sn/sn-site-search-rule.model";
import { TurSNSiteSearchRuleService } from "@/services/sn/sn.site.search.rule.service";
import { type BreadcrumbItem } from "@/contexts/breadcrumb.context";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

const turSNSiteSearchRuleService = new TurSNSiteSearchRuleService();

export default function SNSiteSearchRulePage() {
    const navigate = useNavigate();
    const { id, searchRuleId } = useParams() as { id: string; searchRuleId: string };
    const [searchRule, setSearchRule] = useState<TurSNSiteSearchRule>();
    const [isNew, setIsNew] = useState<boolean>(true);
    const [open, setOpen] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [breadcrumb, setBreadcrumb] = useState<BreadcrumbItem[]>();
    const { t } = useTranslation();

    useEffect(() => {
        if (searchRuleId === "new") {
            setSearchRule({ name: "", position: 0, enabled: true, conditions: [], actions: [] });
            setBreadcrumb([
                { label: t("sn.searchRule.title"), href: `${ROUTES.SN_INSTANCE}/${id}/search-rule` },
                { label: t("common.new") }
            ]);
        } else {
            turSNSiteSearchRuleService.get(id, searchRuleId).then((s) => {
                setSearchRule(s);
                setBreadcrumb([
                    { label: t("sn.searchRule.title"), href: `${ROUTES.SN_INSTANCE}/${id}/search-rule` },
                    { label: s.name || searchRuleId }
                ]);
            }).catch(() => setError("Connection error or timeout while fetching search rule details."));
            setIsNew(false);
        }
    }, [id, searchRuleId]);

    useSubPageBreadcrumb(breadcrumb);

    async function onDelete() {
        if (!searchRule?.id) return;
        try {
            if (await turSNSiteSearchRuleService.delete(id, searchRule.id)) {
                toast.success(t("sn.searchRule.deleted"));
                navigate(`${ROUTES.SN_INSTANCE}/${id}/search-rule`);
            } else {
                toast.error(t("sn.searchRule.notDeleted"));
            }
        } catch (error) {
            console.error("Delete error", error);
            toast.error(t("sn.searchRule.notDeleted"));
        }
        setOpen(false);
    }

    return (
        <LoadProvider checkIsNotUndefined={searchRule} error={error} tryAgainUrl={`${ROUTES.SN_INSTANCE}/${id}/search-rule`}>
            {searchRule && <SNSiteSearchRuleForm snSiteId={id} value={searchRule} isNew={isNew} onDelete={isNew ? undefined : onDelete} open={open} setOpen={setOpen} />}
        </LoadProvider>
    );
}
