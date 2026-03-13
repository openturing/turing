import { Page } from "@/components/page"
import { IconLayoutDashboard } from "@tabler/icons-react"
import { ROUTES } from "../routes.const"

export default function ConsolePage() {
  return (
    <Page turIcon={IconLayoutDashboard} title="Administration Console" urlBase={ROUTES.CONSOLE} />
  )
}
