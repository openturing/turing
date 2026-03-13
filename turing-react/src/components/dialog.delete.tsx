import { ROUTES } from "@/app/routes.const";
import type { TurSECoreSiteUsage } from "@/models/se/se-core-site-usage.model";
import { IconAlertTriangle, IconTrash } from "@tabler/icons-react";
import React, { type Dispatch, type SetStateAction, useState } from "react";
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
}
export const DialogDelete: React.FC<Props> = ({
  feature, name, onDelete, open, setOpen,
  trigger,
  blockedBy,
}) => {
  const isBlocked = blockedBy && blockedBy.length > 0;
  const navigate = useNavigate();
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
              <DialogTitle className="text-center">Cannot delete {feature}</DialogTitle>
              <DialogDescription className="text-center">
                <span className="font-semibold text-foreground">{name}</span> is currently in use and cannot be removed.
              </DialogDescription>
            </DialogHeader>
            <div className="space-y-3">
              <p className="text-sm text-muted-foreground">The following Semantic Navigation sites depend on this {feature}:</p>
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
              <p className="text-xs text-muted-foreground">Remove this {feature} from those sites before deleting it.</p>
            </div>
            <DialogFooter>
              <GradientButton onClick={() => setOpen(false)} variant="outline" className="w-full">
                Understood
              </GradientButton>
            </DialogFooter>
          </>
        ) : (
          <>
            <DialogHeader>
              <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-red-500/10 ring-1 ring-red-500/20 mb-2">
                <IconTrash className="size-6 text-red-500" />
              </div>
              <DialogTitle className="text-center">Delete {feature}</DialogTitle>
              <DialogDescription className="text-center">
                This will permanently delete <span className="font-semibold text-foreground">{name}</span>. This action cannot be undone.
              </DialogDescription>
            </DialogHeader>
            <div className="space-y-2">
              <label className="text-sm text-muted-foreground">
                Type <span className="font-mono font-semibold text-foreground bg-muted px-1.5 py-0.5 rounded">{name}</span> to confirm
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
                Delete {feature}
              </GradientButton>
              <GradientButton
                onClick={() => handleOpenChange(false)}
                variant="ghost"
                className="w-full"
              >
                Cancel
              </GradientButton>
            </DialogFooter>
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}
