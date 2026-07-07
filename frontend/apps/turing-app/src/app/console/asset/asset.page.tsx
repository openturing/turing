import { ROUTES } from "@/app/routes.const"
import { AssetBrowser } from "@/components/asset/asset-browser"
import { PageHeader } from "@/components/page-header"
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb"
import { IconFolder } from "@tabler/icons-react"

export default function AssetPage() {
  useSubPageBreadcrumb("Assets")
  return (
    <>
      <PageHeader turIcon={IconFolder} title="Assets" />
      <AssetBrowser tryAgainUrl={ROUTES.ASSET_ROOT} className="px-4 lg:px-6 pt-4" />
    </>
  )
}
