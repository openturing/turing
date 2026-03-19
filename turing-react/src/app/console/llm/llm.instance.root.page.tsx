import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconCpu2 } from "@tabler/icons-react";

export default function LLMInstanceRootPage() {
  useSubPageBreadcrumb({ label: "Language Model", href: `${ROUTES.LLM_INSTANCE}` });
  return (
    <Page turIcon={IconCpu2} title="Language Model" urlBase={ROUTES.LLM_INSTANCE} />
  )
}
