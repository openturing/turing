import { ROUTES } from "@/app/routes.const";
import type { TurSECoreSiteUsage } from "@/models/se/se-core-site-usage.model";
import { IconAlertTriangle, IconTrash } from "@tabler/icons-react";
import React, { type Dispatch, type SetStateAction, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { BadgeColorful } from "./badge-colorful";
import { getLocaleCountryCode } from "./badge-locale";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from "./ui/dialog";
import { GradientButton } from "./ui/gradient-button";
import { Input } from "./ui/input";

interface Props {
  feature: string;
  name: string;
  onDelete: () => void;
  open: boolean;
  setOpen: Dispatch<SetStateAction<boolean>>;
  trigger?: React.ReactNode;
  blockedBy?: TurSECoreSiteUsage[];
  title?: string;
  description?: string;
  confirmLabel?: string;
}
export const DialogDelete: React.FC<Props> = ({
  feature, name, onDelete, open, setOpen,
  trigger,
  blockedBy,
  title,
  description,
  confirmLabel,
}) => {
  const isBlocked = blockedBy && blockedBy.length > 0;
  const navigate = useNavigate();
  const { t } = useTranslation();
  const [confirmText, setConfirmText] = useState("");

  const canDelete = confirmText === name;

  function handleOpenChange(value: boolean) {
    setOpen(value);
    if (!value) setConfirmText("");
  }

  const defaultTrigger = (
    <GradientButton variant="ghost" size="icon-sm">
      <IconTrash className="size-5!" />
    </GradientButton>
  );

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>
        {trigger ?? defaultTrigger}
      </DialogTrigger>
      <DialogContent className="sm:max-w-md">
        {isBlocked ? (
          <>
            <DialogHeader>
              <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-amber-500/10 ring-1 ring-amber-500/20 mb-2">
                <IconAlertTriangle className="size-6 text-amber-500" />
              </div>
              <DialogTitle className="text-center">{t("dialog.cannotDelete", { feature })}</DialogTitle>
              <DialogDescription className="text-center">
                {t("dialog.inUse", { name })}
              </DialogDescription>
            </DialogHeader>
            <div className="space-y-3">
              <p className="text-sm text-muted-foreground">{t("dialog.dependingSites", { feature })}</p>
              <div className="flex flex-wrap gap-2">
                {blockedBy.map((usage) => {
                  const countryCode = getLocaleCountryCode(usage.language);
                  return (
                    <BadgeColorful
                      key={usage.localeId}
                      text={usage.siteName}
                      href={`${ROUTES.SN_INSTANCE}/${usage.siteId}/locale/${usage.localeId}`}
                      onClick={(href) => { setOpen(false); navigate(href); }}
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
              <p className="text-xs text-muted-foreground">{t("dialog.removeDependencies", { feature })}</p>
            </div>
            <DialogFooter>
              <GradientButton onClick={() => setOpen(false)} variant="outline" className="w-full">
                {t("dialog.understood")}
              </GradientButton>
            </DialogFooter>
          </>
        ) : (
          <>
            <DialogHeader>
              <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-red-500/10 ring-1 ring-red-500/20 mb-2">
                <IconTrash className="size-6 text-red-500" />
              </div>
              <DialogTitle className="text-center">{title ?? t("dialog.deleteFeature", { feature })}</DialogTitle>
              <DialogDescription className="text-center">
                {description ?? t("dialog.permanentDelete", { name })}
              </DialogDescription>
            </DialogHeader>
            <div className="space-y-2">
              <label className="text-sm text-muted-foreground">
                {t("dialog.typeToConfirm")} <span className="font-mono font-semibold text-foreground bg-muted px-1.5 py-0.5 rounded">{name}</span> {t("dialog.toConfirm")}
              </label>
              <Input
                value={confirmText}
                onChange={(e) => setConfirmText(e.target.value)}
                placeholder={name}
                className="font-mono text-sm"
                autoFocus
              />
            </div>
            <DialogFooter className="flex-col gap-2 sm:flex-col">
              <GradientButton
                onClick={onDelete}
                variant="destructive"
                disabled={!canDelete}
                className="w-full"
              >
                <IconTrash className="size-4" />
                {confirmLabel ?? t("dialog.deleteFeature", { feature })}
              </GradientButton>
              <GradientButton
                onClick={() => handleOpenChange(false)}
                variant="ghost"
                className="w-full"
              >
                {t("common.cancel")}
              </GradientButton>
            </DialogFooter>
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}
