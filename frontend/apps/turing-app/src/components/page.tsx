import { PageContent } from "./page-content";
import { PageHeader } from "./page-header";

interface MyComponentProps {
  turIcon?: React.ElementType;
  title: string;
  urlBase?: string;
}


export const Page: React.FC<MyComponentProps> = ({ turIcon: TurIcon, title, urlBase }) => {
  return (
    <>
      <PageHeader turIcon={TurIcon} title={title} urlBase={urlBase} />
      <PageContent />
    </>
  )
}