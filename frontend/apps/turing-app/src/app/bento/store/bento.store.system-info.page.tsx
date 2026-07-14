import { ROUTES } from "@/app/routes.const";
import StoreInstanceSystemInfoPage from "@/app/console/store/system-info/store.instance.system-info.page";
import { BentoHero } from "@/components/bento";
import { SectionCardChromeProvider } from "@/components/ui/section-card";
import { IconInfoCircle } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

/**
 * Bento Embedding Store system information — reuses the console
 * {@link StoreInstanceSystemInfoPage} body with a {@link BentoHero} header.
 */
export default function BentoStoreSystemInfoPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const instanceRoute = `${ROUTES.BENTO_STORE_INSTANCE}/${id}`;

  return (
    <SectionCardChromeProvider chrome="bento">
      <StoreInstanceSystemInfoPage
        header={
          <BentoHero
            backTo={instanceRoute}
            backLabel={t("store.title")}
            leading={
              <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-violet-500 to-purple-600 text-white shadow-md">
                <IconInfoCircle size={24} />
              </span>
            }
            title={t("store.systemInfo.title")}
            subtitle={t("store.systemInfo.description")}
          />
        }
      />
    </SectionCardChromeProvider>
  );
}
