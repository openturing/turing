import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconCube } from "@tabler/icons-react";

export default function EmbeddingModelRootPage() {
  useSubPageBreadcrumb({ label: "Embedding Model", href: `${ROUTES.EMBEDDING_MODEL_INSTANCE}` });
  return (
    <Page turIcon={IconCube} title="Embedding Model" urlBase={ROUTES.EMBEDDING_MODEL_INSTANCE} />
  )
}
