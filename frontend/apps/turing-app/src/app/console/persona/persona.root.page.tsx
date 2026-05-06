import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconUserCircle } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

export default function PersonaRootPage() {
  const { t } = useTranslation();
  useSubPageBreadcrumb({ label: t("persona.title"), href: `${ROUTES.PERSONA_INSTANCE}` });
  return (
    <Page turIcon={IconUserCircle} title={t("persona.title")} urlBase={ROUTES.PERSONA_INSTANCE} />
  )
}
