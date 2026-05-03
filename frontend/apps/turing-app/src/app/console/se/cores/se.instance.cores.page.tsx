import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate";
import { BadgeColorful } from "@/components/badge-colorful";
import { BadgeLocale, getLocaleCountryCode } from "@/components/badge-locale";
import { DialogDelete } from "@/components/dialog.delete";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import type { TurSECoreInfo } from "@/models/se/se-core-info.model.ts";
import { useClearSeCore, useDeleteSeCore, useSeCores } from "@/api/queries/se-core.queries";
import {
  IconChevronDown,
  IconDatabase,
  IconEraser,
  IconPlus,
  IconSearch,
  IconServer,
  IconTrash,
  IconX,
} from "@tabler/icons-react";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

// --- Core grouping ---

interface LocaleEntry {
  locale: string;
  coreInfo: TurSECoreInfo;
}

interface CoreGroup {
  base: string;
  locales: LocaleEntry[];
  coreInfo?: TurSECoreInfo; // only for leaf cores (no locale children)
}

// Matches: {base}_{lang}_{COUNTRY} or {base}_{lang}
const LOCALE_PATTERN = /^(.+)_([a-z]{2}_[A-Z]{2})$|^(.+)_([a-z]{2})$/;

function groupCores(cores: TurSECoreInfo[]): CoreGroup[] {
  const map = new Map<string, LocaleEntry[]>();
  const leafMap = new Map<string, TurSECoreInfo>();

  for (const coreInfo of cores) {
    const m = LOCALE_PATTERN.exec(coreInfo.name);
    if (m) {
      const base = m[1] ?? m[3];
      const locale = m[2] ?? m[4];
      if (!map.has(base)) map.set(base, []);
      map.get(base)?.push({ locale, coreInfo });
    } else if (!map.has(coreInfo.name)) {
      map.set(coreInfo.name, []);
      leafMap.set(coreInfo.name, coreInfo);
    }
  }

  return Array.from(map.entries())
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([base, locales]) => ({ base, locales, coreInfo: leafMap.get(base) }));
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
  const { t } = useTranslation();
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [clearOpen, setClearOpen] = useState(false);
  const navigate = useNavigate();

  return (
    <div className="flex items-center gap-3 px-4 py-2.5 pl-10 bg-muted/20">
      <BadgeLocale locale={locale} />
      <span className="text-sm text-muted-foreground flex-1 font-mono">{coreInfo.name}</span>
      {coreInfo.usedBySites.length > 0 && (
        <div className="flex flex-wrap gap-1">
          {coreInfo.usedBySites.map((usage) => {
            const countryCode = getLocaleCountryCode(usage.language);
            return (
              <BadgeColorful
                key={usage.localeId}
                text={usage.siteName}
                href={`${ROUTES.SN_INSTANCE}/${usage.siteId}/locale/${usage.localeId}`}
                onClick={navigate}
                prefix={countryCode ? (
                  <img
                    src={`https://flagcdn.com/w40/${countryCode}.png`}
                    alt={usage.language}
                    className="w-4 h-3 object-cover rounded-sm"
                  />
                ) : undefined}
              />
            );
          })}
        </div>
      )}
      <span className="text-xs text-muted-foreground tabular-nums">
        {formatNumDocs(coreInfo.numDocs)} {t("se.cores.docs")}
      </span>
      <div className="flex items-center gap-1">
        <DialogDelete
          feature="documents"
          name={coreInfo.name}
          open={clearOpen}
          setOpen={setClearOpen}
          onDelete={() => { setClearOpen(false); onClear(coreInfo.name); }}
          trigger={
            <Button variant="ghost" size="icon-sm" title={t("se.cores.removeAllDocs")}>
              <IconEraser className="size-4" />
            </Button>
          }
          title={t("se.cores.removeAllDocs") + "?"}
          description={t("se.cores.removeAllDocsDesc")}
          confirmLabel={t("se.cores.confirmRemoveAll")}
        />
        <DialogDelete
          feature="core"
          name={coreInfo.name}
          open={deleteOpen}
          setOpen={setDeleteOpen}
          onDelete={() => { setDeleteOpen(false); onDelete(coreInfo.name); }}
          blockedBy={coreInfo.usedBySites}
          trigger={
            <Button variant="ghost" size="icon-sm" title={t("se.cores.deleteCore")}>
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
  const { t } = useTranslation();
  const [open, setOpen] = useState(true);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [clearOpen, setClearOpen] = useState(false);
  const navigate = useNavigate();
  const hasLocales = group.locales.length > 0;
  const localeCount = group.locales.length;
  const totalDocs = group.locales.reduce((sum, { coreInfo }) => sum + coreInfo.numDocs, 0);

  return (
    <div className="rounded-lg border bg-background overflow-hidden">
      <div className="flex items-center gap-3 px-4 py-3 hover:bg-muted/50 transition-colors">
        {hasLocales ? (
          <button
            type="button"
            onClick={() => setOpen((v) => !v)}
            className="flex flex-1 items-center gap-3 text-left min-w-0"
          >
            <IconServer className="size-4 text-muted-foreground shrink-0" />
            <span className="text-sm font-semibold flex-1">{group.base}</span>
            <span className="text-xs text-muted-foreground">
              {formatNumDocs(totalDocs)} {t("se.cores.docs")}
            </span>
            <span className="text-xs text-muted-foreground">&middot;</span>
            <span className="text-xs text-muted-foreground">
              {localeCount} {localeCount === 1 ? t("se.cores.locale") : t("se.cores.locales")}
            </span>
            <IconChevronDown
              className={`size-4 text-muted-foreground transition-transform duration-200 ${open ? "rotate-180" : ""}`}
            />
          </button>
        ) : (
          <>
            <IconServer className="size-4 text-muted-foreground shrink-0" />
            <span className="text-sm font-semibold flex-1">{group.base}</span>
            {group.coreInfo && group.coreInfo.usedBySites.length > 0 && (
              <div className="flex flex-wrap gap-1">
                {group.coreInfo.usedBySites.map((usage) => {
                  const countryCode = getLocaleCountryCode(usage.language);
                  return (
                    <BadgeColorful
                      key={usage.localeId}
                      text={usage.siteName}
                      href={`${ROUTES.SN_INSTANCE}/${usage.siteId}/locale/${usage.localeId}`}
                      onClick={navigate}
                      prefix={countryCode ? (
                        <img
                          src={`https://flagcdn.com/w40/${countryCode}.png`}
                          alt={usage.language}
                          className="w-4 h-3 object-cover rounded-sm"
                        />
                      ) : undefined}
                    />
                  );
                })}
              </div>
            )}
            {group.coreInfo && (
              <span className="text-xs text-muted-foreground tabular-nums">
                {formatNumDocs(group.coreInfo.numDocs)} {t("se.cores.docs")}
              </span>
            )}
            {group.coreInfo && (
              <div className="flex items-center gap-1">
                <DialogDelete
                  feature="documents"
                  name={group.coreInfo.name}
                  open={clearOpen}
                  setOpen={setClearOpen}
                  onDelete={() => { setClearOpen(false); onClear(group.coreInfo!.name); }}
                  trigger={
                    <Button variant="ghost" size="icon-sm" title={t("se.cores.removeAllDocs")}>
                      <IconEraser className="size-4" />
                    </Button>
                  }
                  title={t("se.cores.removeAllDocs") + "?"}
                  description={t("se.cores.removeAllDocsDesc")}
                  confirmLabel={t("se.cores.confirmRemoveAll")}
                />
                <DialogDelete
                  feature="core"
                  name={group.coreInfo.name}
                  open={deleteOpen}
                  setOpen={setDeleteOpen}
                  onDelete={() => { setDeleteOpen(false); onDelete(group.coreInfo!.name); }}
                  blockedBy={group.coreInfo.usedBySites}
                  trigger={
                    <Button variant="ghost" size="icon-sm" title={t("se.cores.deleteCore")}>
                      <IconTrash className="size-4" />
                    </Button>
                  }
                />
              </div>
            )}
          </>
        )}
      </div>

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
  const { t } = useTranslation();
  const { id } = useParams() as { id: string };
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  useSubPageBreadcrumb(t("se.cores.title"));

  const { data: cores, isError } = useSeCores(id);
  const error = isError ? t("common.connectionError", { resource: "Solr cores" }) : null;
  const deleteCoreMutation = useDeleteSeCore();
  const clearCoreMutation = useClearSeCore();

  const handleDelete = (coreName: string) => {
    deleteCoreMutation.mutate(
      { seId: id, coreName },
      {
        onError: () => toast.error(t("se.cores.deleteFailed", { name: coreName })),
      },
    );
  };

  const handleClear = (coreName: string) => {
    clearCoreMutation.mutate(
      { seId: id, coreName },
      {
        onError: () => toast.error(t("se.cores.removeDocsFailed", { name: coreName })),
      },
    );
  };

  const [search, setSearch] = useState(() => searchParams.get("search") ?? "");
  const allGroups = useMemo(() => (cores ? groupCores(cores) : []), [cores]);

  const groups = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return allGroups;
    return allGroups
      .map((group) => {
        const baseMatch = group.base.toLowerCase().includes(q);
        if (baseMatch) return group;
        const filteredLocales = group.locales.filter(({ coreInfo, locale }) =>
          coreInfo.name.toLowerCase().includes(q) || locale.toLowerCase().includes(q)
        );
        if (filteredLocales.length > 0) return { ...group, locales: filteredLocales };
        return null;
      })
      .filter((g): g is NonNullable<typeof g> => g !== null);
  }, [allGroups, search]);

  return (
    <LoadProvider checkIsNotUndefined={cores} error={error} tryAgainUrl={`${ROUTES.SE_INSTANCE}/${id}/cores`}>
      {cores && cores.length > 0 ? (
        <>
          <SubPageHeader
            icon={IconDatabase}
            name={t("se.cores.title")}
            feature={t("se.cores.title")}
            description={t("se.cores.description")}
          />
          <div className="px-6 pb-6 space-y-4">
            <div className="flex items-center gap-3">
              <div className="relative flex-1">
                <IconSearch className="absolute left-3 top-1/2 -translate-y-1/2 size-4 text-muted-foreground pointer-events-none" />
                <Input
                  placeholder={t("se.cores.searchCores")}
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  className="pl-9 pr-9"
                />
                {search && (
                  <button
                    type="button"
                    onClick={() => setSearch("")}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                    aria-label={t("se.cores.clearSearch")}
                  >
                    <IconX className="size-4" />
                  </button>
                )}
              </div>
              <Button variant="outline" onClick={() => navigate(`${ROUTES.SE_INSTANCE}/${id}/cores/new`)}>
                <IconPlus className="size-4 mr-2" />
                {t("se.cores.newCore")}
              </Button>
            </div>
            {groups.length > 0 ? (
              <div className="space-y-2">
                {groups.map((group) => (
                  <CoreGroupRow
                    key={group.base}
                    group={group}
                    onDelete={handleDelete}
                    onClear={handleClear}
                  />
                ))}
              </div>
            ) : (
              <p className="text-sm text-muted-foreground text-center py-8">
                {t("se.cores.noMatch")} <span className="font-semibold">"{search}"</span>.
              </p>
            )}
          </div>
        </>
      ) : (
        <BlankSlate
          icon={IconDatabase}
          title={t("se.cores.blankTitle")}
          description={t("se.cores.blankDescription")}
          buttonText={t("se.cores.newCore")}
          urlNew={`${ROUTES.SE_INSTANCE}/${id}/cores/new`}
        />
      )}
    </LoadProvider>
  );
}
