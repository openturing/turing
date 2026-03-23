import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconCpu2 } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

export default function LLMInstanceRootPage() {
  const { t } = useTranslation();
  useSubPageBreadcrumb({ label: t("llm.title"), href: `${ROUTES.LLM_INSTANCE}` });
  return (
    <Page turIcon={IconCpu2} title={t("llm.title")} urlBase={ROUTES.LLM_INSTANCE} />
  )
}
