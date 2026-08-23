import { ROUTES } from "@/app/routes.const";
import StoreInstanceCollectionImportPage from "@/app/console/store/collections/store.instance.collection.import.page";
import { BentoHero, bentoChipClass } from "@/components/bento";
import { IconPackageImport } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

/**
 * Bento Embedding Store collection import — reuses the console
 * {@link StoreInstanceCollectionImportPage} drag-and-drop uploader with a
 * {@link BentoHero} header. "Back" returns to the bento detail page, where
 * the collections list renders inline.
 */
export default function BentoStoreCollectionImportPage() {
  const { id, collectionName } = useParams() as { id: string; collectionName: string };
  const { t } = useTranslation();
  const instanceRoute = `${ROUTES.BENTO_STORE_INSTANCE}/${id}`;

  return (
    <StoreInstanceCollectionImportPage
      backHref={instanceRoute}
      header={
        <BentoHero
          backTo={instanceRoute}
          backLabel={t("store.title")}
          leading={
            <span className={`grid h-12 w-12 place-items-center rounded-2xl ${bentoChipClass("violet")} text-white shadow-md`}>
              <IconPackageImport size={24} />
            </span>
          }
          title={t("store.collections.importTitle", { name: collectionName })}
          subtitle={t("store.collections.importDescription")}
        />
      }
    />
  );
}
