import { ROUTES } from "@/app/routes.const"
import { LoadProvider } from "@/components/loading-provider"
import { StoreInstanceForm } from "@/components/store/store.instance.form"
import { SubPageHeader } from "@/components/sub.page.header"
import type { TurStoreInstance } from "@/models/store/store-instance.model"
import { TurStoreInstanceService } from "@/services/store/store.service"
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb"
import { IconSettings } from "@tabler/icons-react"
import { useEffect, useState } from "react"
import { useParams } from "react-router-dom"

const turStoreInstanceService = new TurStoreInstanceService()

export default function StoreInstanceDetailPage() {
  const { id } = useParams() as { id: string }
  const [storeInstance, setStoreInstance] = useState<TurStoreInstance>()
  const [isNew, setIsNew] = useState(true)
  const [error, setError] = useState<string | null>(null)
  useSubPageBreadcrumb("Settings")

  useEffect(() => {
    if (id === "new") {
      setStoreInstance({} as TurStoreInstance)
    } else {
      turStoreInstanceService.get(id)
        .then(setStoreInstance)
        .catch(() => setError("Connection error or timeout while fetching Embedding Store instance."))
      setIsNew(false)
    }
  }, [id])

  return (
    <LoadProvider checkIsNotUndefined={storeInstance} error={error} tryAgainUrl={`${ROUTES.STORE_INSTANCE}/${id}`}>
      <SubPageHeader icon={IconSettings} feature="Embedding Store" name="Settings"
        description="Configure the embedding store connection and options." />
      {storeInstance && <StoreInstanceForm value={storeInstance} isNew={isNew} />}
    </LoadProvider>
  )
}
