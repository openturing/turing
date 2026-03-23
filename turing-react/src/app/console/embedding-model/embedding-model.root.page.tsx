import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconCube } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

export default function EmbeddingModelRootPage() {
  const { t } = useTranslation();
  useSubPageBreadcrumb({ label: t("embeddingModel.title"), href: `${ROUTES.EMBEDDING_MODEL_INSTANCE}` });
  return (
    <Page turIcon={IconCube} title={t("embeddingModel.title")} urlBase={ROUTES.EMBEDDING_MODEL_INSTANCE} />
  )
}
