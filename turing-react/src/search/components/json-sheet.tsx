import { JsonViewer } from "@/components/json-viewer";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { IconBraces } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

interface JsonSheetProps {
  data: Record<string, unknown> | null;
  onClose: () => void;
}

export function JsonSheet({ data, onClose }: Readonly<JsonSheetProps>) {
  const { t } = useTranslation();

  return (
    <Sheet open={data !== null} onOpenChange={(open) => { if (!open) onClose(); }}>
      <SheetContent side="right" className="w-full sm:max-w-2xl flex flex-col">
        <SheetHeader>
          <SheetTitle className="flex items-center gap-2">
            <IconBraces className="size-5" />
            {t("search.documentJson")}
          </SheetTitle>
        </SheetHeader>
        <div className="flex-1 overflow-auto mt-4">
          <JsonViewer data={data} className="h-full" />
        </div>
      </SheetContent>
    </Sheet>
  );
}
