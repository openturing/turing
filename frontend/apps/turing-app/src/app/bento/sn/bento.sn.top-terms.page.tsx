import { ROUTES } from "@/app/routes.const";
import SNSiteTopSearchTermsPage from "@/app/console/sn/top-search-terms/sn.site.top.search.terms.page";
import { BentoHero } from "@/components/bento";
import { IconChartBar } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

/**
 * Bento SN top search terms — T576. Reuses the console page (self-contained
 * tabs + tables) with the bento base route (tab navigation stays in the shell)
 * and a `BentoHero` header instead of the sidebar-coupled `SubPageHeader`.
 */
export default function BentoSNTopTermsPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const instanceRoute = `${ROUTES.BENTO_SN_INSTANCE}/${id}`;

  return (
    <SNSiteTopSearchTermsPage
      baseRoute={ROUTES.BENTO_SN_INSTANCE}
      header={
        <BentoHero
          backTo={instanceRoute}
          backLabel={t("sn.title")}
          leading={
            <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-emerald-500 to-teal-600 text-white shadow-md">
              <IconChartBar size={24} />
            </span>
          }
          title={t("sn.topSearchTerms.title")}
          subtitle={t("sn.topSearchTerms.description")}
        />
      }
    />
  );
}
