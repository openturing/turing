import { Page } from "@/components/page"
import { IconLayoutDashboard } from "@tabler/icons-react"
import { useTranslation } from "react-i18next"
import { ROUTES } from "../routes.const"

export default function ConsolePage() {
  const { t } = useTranslation()
  return (
    <Page turIcon={IconLayoutDashboard} title={t("admin.console")} urlBase={ROUTES.CONSOLE} />
  )
}
