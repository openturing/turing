import { useCreateSkill, useImportSkillZip, useSkills } from "@/api/queries/skill.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoHero } from "@/components/bento";
import { LoadProvider } from "@/components/loading-provider";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { GradientButton } from "@/components/ui/gradient-button";
import { Input } from "@/components/ui/input";
import { TurSkillService } from "@/services/skill/skill.service";
import { IconDownload, IconFileImport, IconPlus, IconSparkles } from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";
import { useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";

const service = new TurSkillService();

/**
 * Bento skill catalog — T560. The skill folder editor is an app-like surface
 * (file-tree + code editor), not a hero/save-bar CRUD, so it "sits inside the
 * shell" rather than adopting the entity pattern. This list re-skins the
 * console skill catalog onto a `BentoHero` + frosted tiles, reusing the same
 * React-Query hooks; tiles open the Bento skill editor.
 */
export default function BentoSkillListPage() {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const { data: skills, isError } = useSkills();
  const createSkill = useCreateSkill();
  const importZip = useImportSkillZip();
  const [createOpen, setCreateOpen] = useState(false);
  const [newName, setNewName] = useState("");
  const fileInputRef = useRef<HTMLInputElement>(null);

  const error = isError ? t("common.connectionError", { resource: t("home.features.skills.title") }) : null;

  const handleCreate = async () => {
    const name = newName.trim();
    if (!name) return;
    try {
      const created = await createSkill.mutateAsync(name);
      toast.success(t("skill.created", { name: created.name }));
      setCreateOpen(false);
      setNewName("");
      navigate(`${ROUTES.BENTO_SKILL}/${created.id}`);
    } catch {
      toast.error(t("skill.createFailed"));
    }
  };

  const handleExport = async (id: string, name: string) => {
    try {
      await service.download(id, name);
    } catch {
      toast.error(t("skill.exportFailed"));
    }
  };

  const handleImport = async (files: FileList | null) => {
    const file = files?.[0];
    if (!file) return;
    try {
      const imported = await importZip.mutateAsync(file);
      toast.success(t("skill.imported", { count: imported.length, names: imported.join(", ") }));
    } catch {
      toast.error(t("skill.importFailed"));
    } finally {
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  };

  return (
    <LoadProvider checkIsNotUndefined={skills} error={error} tryAgainUrl={ROUTES.BENTO_SKILL}>
      <input
        ref={fileInputRef}
        type="file"
        accept=".zip,application/zip"
        className="hidden"
        title={t("skill.importZipTitle")}
        onChange={(e) => handleImport(e.target.files)}
      />
      <BentoHero
        backTo={ROUTES.BENTO_AREA_GENERATIVE_AI}
        backLabel={t("home.sections.generativeAi.label")}
        leading={
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-violet-500 to-purple-600 text-white shadow-md">
            <IconSparkles size={24} />
          </span>
        }
        title={t("home.features.skills.title")}
        subtitle={t("home.features.skills.description")}
        trailing={
          <>
            <GradientButton size="sm" onClick={() => { setNewName(""); setCreateOpen(true); }}>
              <IconPlus className="size-4" />
              {t("skill.newSkill")}
            </GradientButton>
            <Button variant="outline" size="sm" disabled={importZip.isPending} onClick={() => fileInputRef.current?.click()}>
              <IconFileImport className="size-4 mr-1.5" />
              {importZip.isPending ? t("skill.importing") : t("skill.importZip")}
            </Button>
          </>
        }
      />

      {skills && skills.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-16 text-muted-foreground">
          <IconSparkles className="size-12 mb-3 opacity-40" />
          <p className="text-sm">{t("skill.emptyHint")}</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {skills?.map((skill) => (
            <div
              key={skill.id}
              role="button"
              tabIndex={0}
              onClick={() => navigate(`${ROUTES.BENTO_SKILL}/${skill.id}`)}
              onKeyDown={(e) => { if (e.key === "Enter") navigate(`${ROUTES.BENTO_SKILL}/${skill.id}`); }}
              className="bento-tile bento-tile-clickable bento-glass group flex cursor-pointer items-center gap-3 rounded-2xl p-4 text-left"
            >
              <span className="grid size-9 shrink-0 place-items-center rounded-xl bg-linear-to-br from-violet-500 to-purple-600 text-white shadow-sm">
                <IconSparkles className="size-4.5" />
              </span>
              <span className="min-w-0 flex-1">
                <span className="block truncate font-medium">{skill.name}</span>
                {skill.version && <span className="block truncate text-xs text-muted-foreground">v{skill.version}</span>}
              </span>
              {!skill.enabled && <Badge variant="outline" className="shrink-0">{t("skill.disabled")}</Badge>}
              <button
                type="button"
                title={t("skill.exportZip")}
                className="shrink-0 rounded-md p-1.5 text-muted-foreground opacity-0 transition-opacity hover:bg-accent hover:text-foreground group-hover:opacity-100"
                onClick={(e) => { e.stopPropagation(); handleExport(skill.id, skill.name); }}
              >
                <IconDownload className="size-4" />
              </button>
            </div>
          ))}
        </div>
      )}

      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent>
          <DialogHeader><DialogTitle>{t("skill.newSkill")}</DialogTitle></DialogHeader>
          <Input
            placeholder={t("skill.namePlaceholder")}
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            onKeyDown={(e) => { if (e.key === "Enter") handleCreate(); }}
            autoFocus
          />
          <DialogFooter>
            <Button variant="outline" onClick={() => setCreateOpen(false)}>{t("common.cancel")}</Button>
            <GradientButton onClick={handleCreate} disabled={!newName.trim() || createSkill.isPending}>{t("skill.create")}</GradientButton>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </LoadProvider>
  );
}
