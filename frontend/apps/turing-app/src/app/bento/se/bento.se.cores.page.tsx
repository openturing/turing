import { ROUTES } from "@/app/routes.const";
import SEInstanceCoresPage from "@/app/console/se/cores/se.instance.cores.page";
import { BentoHero } from "@/components/bento";
import { IconDatabase } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

/**
 * Bento Search Engine cores (Solr cores / collections) — reuses the console
 * {@link SEInstanceCoresPage} body with a {@link BentoHero} header and the
 * bento base route so the "New Core" and try-again links stay in `/bento`.
 */
export default function BentoSECoresPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const instanceRoute = `${ROUTES.BENTO_SE_INSTANCE}/${id}`;

  return (
    <SEInstanceCoresPage
      baseRoute={ROUTES.BENTO_SE_INSTANCE}
      header={
        <BentoHero
          backTo={instanceRoute}
          backLabel={t("se.title")}
          leading={
            <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-emerald-500 to-teal-600 text-white shadow-md">
              <IconDatabase size={24} />
            </span>
          }
          title={t("se.cores.title")}
          subtitle={t("se.cores.description")}
        />
      }
    />
  );
}
