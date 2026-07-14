import { ROUTES } from "@/app/routes.const";
import SEInstanceSystemInfoPage from "@/app/console/se/system-info/se.instance.system-info.page";
import { BentoHero } from "@/components/bento";
import { SectionCardChromeProvider } from "@/components/ui/section-card";
import { IconInfoCircle } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

/**
 * Bento Search Engine system information — reuses the console
 * {@link SEInstanceSystemInfoPage} body with a {@link BentoHero} header.
 */
export default function BentoSESystemInfoPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const instanceRoute = `${ROUTES.BENTO_SE_INSTANCE}/${id}`;

  return (
    <SectionCardChromeProvider chrome="bento">
      <SEInstanceSystemInfoPage
        header={
          <BentoHero
            backTo={instanceRoute}
            backLabel={t("se.title")}
            leading={
              <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-emerald-500 to-teal-600 text-white shadow-md">
                <IconInfoCircle size={24} />
              </span>
            }
            title={t("se.systemInfo.title")}
            subtitle={t("se.systemInfo.description")}
          />
        }
      />
    </SectionCardChromeProvider>
  );
}
