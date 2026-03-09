import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate";
import { BadgeLocale } from "@/components/badge-locale";
import { DialogDelete } from "@/components/dialog.delete";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { Button } from "@/components/ui/button";
import type { TurSECoreInfo } from "@/models/se/se-core-info.model.ts";
import { TurSEInstanceService } from "@/services/se/se.service";
import {
  IconChevronDown,
  IconDatabase,
  IconEraser,
  IconServer,
  IconTrash,
} from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";

const turSEInstanceService = new TurSEInstanceService();

// --- Core grouping ---

interface LocaleEntry {
  locale: string;
  coreInfo: TurSECoreInfo;
}

interface CoreGroup {
  base: string;
  locales: LocaleEntry[];
}

// Matches: {base}_{lang}_{COUNTRY} or {base}_{lang}
const LOCALE_PATTERN = /^(.+)_([a-z]{2}_[A-Z]{2})$|^(.+)_([a-z]{2})$/;

function groupCores(cores: TurSECoreInfo[]): CoreGroup[] {
  const map = new Map<string, LocaleEntry[]>();

  for (const coreInfo of cores) {
    const m = LOCALE_PATTERN.exec(coreInfo.name);
    if (m) {
      const base = m[1] ?? m[3];
      const locale = m[2] ?? m[4];
      if (!map.has(base)) map.set(base, []);
      map.get(base)?.push({ locale, coreInfo });
    } else if (!map.has(coreInfo.name)) {
      map.set(coreInfo.name, []);
    }
  }

  return Array.from(map.entries())
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([base, locales]) => ({ base, locales }));
}

function formatNumDocs(n: number): string {
  return n.toLocaleString();
}

// --- Locale row with actions ---

interface LocaleRowProps {
  locale: string;
  coreInfo: TurSECoreInfo;
  onDelete: (coreName: string) => void;
  onClear: (coreName: string) => void;
}

function LocaleRow({ locale, coreInfo, onDelete, onClear }: Readonly<LocaleRowProps>) {
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [clearOpen, setClearOpen] = useState(false);

  return (
    <div className="flex items-center gap-3 px-4 py-2.5 pl-10 bg-muted/20">
      <BadgeLocale locale={locale} />
      <span className="text-sm text-muted-foreground flex-1 font-mono">{coreInfo.name}</span>
      <span className="text-xs text-muted-foreground tabular-nums">
        {formatNumDocs(coreInfo.numDocs)} docs
      </span>
      <div className="flex items-center gap-1">
        <DialogDelete
          feature="documents"
          name={coreInfo.name}
          open={clearOpen}
          setOpen={setClearOpen}
          onDelete={() => { setClearOpen(false); onClear(coreInfo.name); }}
          trigger={
            <Button variant="ghost" size="icon-sm" title="Remove all documents">
              <IconEraser className="size-4" />
            </Button>
          }
          title="Remove all documents?"
          description="This will delete all indexed documents from this core."
          confirmLabel="Yes, remove all documents"
        />
        <DialogDelete
          feature="core"
          name={coreInfo.name}
          open={deleteOpen}
          setOpen={setDeleteOpen}
          onDelete={() => { setDeleteOpen(false); onDelete(coreInfo.name); }}
          trigger={
            <Button variant="ghost" size="icon-sm" title="Delete core">
              <IconTrash className="size-4" />
            </Button>
          }
        />
      </div>
    </div>
  );
}

// --- Group row ---

interface CoreGroupRowProps {
  group: CoreGroup;
  onDelete: (coreName: string) => void;
  onClear: (coreName: string) => void;
}

function CoreGroupRow({ group, onDelete, onClear }: Readonly<CoreGroupRowProps>) {
  const [open, setOpen] = useState(true);
  const hasLocales = group.locales.length > 0;
  const localeCount = group.locales.length;
  const totalDocs = group.locales.reduce((sum, { coreInfo }) => sum + coreInfo.numDocs, 0);

  return (
    <div className="rounded-lg border bg-background overflow-hidden">
      <button
        type="button"
        onClick={() => hasLocales && setOpen((v) => !v)}
        className="flex w-full items-center gap-3 px-4 py-3 text-left hover:bg-muted/50 transition-colors"
      >
        <IconServer className="size-4 text-muted-foreground shrink-0" />
        <span className="text-sm font-semibold flex-1">{group.base}</span>
        {hasLocales && (
          <>
            <span className="text-xs text-muted-foreground">
              {formatNumDocs(totalDocs)} docs
            </span>
            <span className="text-xs text-muted-foreground">·</span>
            <span className="text-xs text-muted-foreground">
              {localeCount} locale{localeCount === 1 ? "" : "s"}
            </span>
            <IconChevronDown
              className={`size-4 text-muted-foreground transition-transform duration-200 ${open ? "rotate-180" : ""}`}
            />
          </>
        )}
      </button>

      {hasLocales && open && (
        <div className="border-t divide-y">
          {group.locales.map(({ locale, coreInfo }) => (
            <LocaleRow
              key={coreInfo.name}
              locale={locale}
              coreInfo={coreInfo}
              onDelete={onDelete}
              onClear={onClear}
            />
          ))}
        </div>
      )}
    </div>
  );
}

export default function SEInstanceCoresPage() {
  const { id } = useParams() as { id: string };
  const [cores, setCores] = useState<TurSECoreInfo[]>();
  const [error, setError] = useState<string | null>(null);

  const loadCores = () => {
    turSEInstanceService.getCores(id)
      .then(setCores)
      .catch(() => setError("Connection error or timeout while fetching Solr cores."));
  };

  useEffect(() => {
    loadCores();
  }, [id]);

  const handleDelete = (coreName: string) => {
    turSEInstanceService.deleteCore(id, coreName)
      .then(loadCores)
      .catch(() => setError(`Failed to delete core: ${coreName}`));
  };

  const handleClear = (coreName: string) => {
    turSEInstanceService.clearCore(id, coreName)
      .then(loadCores)
      .catch(() => setError(`Failed to remove documents from: ${coreName}`));
  };

  const groups = cores ? groupCores(cores) : [];

  return (
    <LoadProvider checkIsNotUndefined={cores} error={error} tryAgainUrl={`${ROUTES.SE_INSTANCE}/${id}/cores`}>
      {cores && cores.length > 0 ? (
        <>
          <SubPageHeader
            icon={IconDatabase}
            name="Cores"
            feature="Cores"
            description="Solr cores and collections available in this Search Engine."
          />
          <div className="px-6 pb-6 space-y-2">
            {groups.map((group) => (
              <CoreGroupRow
                key={group.base}
                group={group}
                onDelete={handleDelete}
                onClear={handleClear}
              />
            ))}
          </div>
        </>
      ) : (
        <BlankSlate
          icon={IconDatabase}
          title="No cores or collections found."
          description="Make sure the Search Engine is running and accessible at the configured endpoint."
          buttonText="Refresh"
          urlNew={`${ROUTES.SE_INSTANCE}/${id}/cores`}
        />
      )}
    </LoadProvider>
  );
}
