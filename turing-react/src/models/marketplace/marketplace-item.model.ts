export interface TurMarketplaceItem {
  id: string
  title: string
  description: string
  version: string
  author: string
  category: string
  tags: string[]
  icon: string
  downloadUrl: string
  readmeUrl?: string
  hasContent: boolean
  hasTemplate: boolean
}
