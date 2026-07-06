import { ROUTES } from "@/app/routes.const";
import SEInstanceCoreNewPage from "@/app/console/se/cores/se.instance.core.new.page";
import { BentoHero } from "@/components/bento";
import { SectionCardChromeProvider } from "@/components/ui/section-card";
import { IconDatabase } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { Link, useParams } from "react-router-dom";

/**
 * Bento "New Core" — reuses the console {@link SEInstanceCoreNewPage} form with
 * a {@link BentoHero} header and the bento base route so save/cancel return to
 * the bento cores list.
 */
export default function BentoSECoreNewPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const coresRoute = `${ROUTES.BENTO_SE_INSTANCE}/${id}/cores`;

  return (
    <SectionCardChromeProvider chrome="bento">
      <SEInstanceCoreNewPage
        baseRoute={ROUTES.BENTO_SE_INSTANCE}
        header={
          <BentoHero
            eyebrow={<Link to={coresRoute} className="hover:text-foreground">{t("se.cores.title")}</Link>}
            leading={
              <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-emerald-500 to-teal-600 text-white shadow-md">
                <IconDatabase size={24} />
              </span>
            }
            title={t("se.newCore.title")}
            subtitle={t("se.newCore.description")}
          />
        }
      />
    </SectionCardChromeProvider>
  );
}
