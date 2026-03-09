import { ROUTES } from "@/app/routes.const";
import type { TurSECoreSiteUsage } from "@/models/se/se-core-site-usage.model";
import { IconTrash } from "@tabler/icons-react";
import React, { type Dispatch, type SetStateAction } from "react";
import { useNavigate } from "react-router-dom";
import { BadgeColorful } from "./badge-colorful";
import { getLocaleCountryCode } from "./badge-locale";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from "./ui/dialog";
import { GradientButton } from "./ui/gradient-button";

interface Props {
  feature: string;
  name: string;
  onDelete: () => void;
  open: boolean;
  setOpen: Dispatch<SetStateAction<boolean>>;
  trigger?: React.ReactNode;
  title?: string;
  description?: string;
  confirmLabel?: string;
  blockedBy?: TurSECoreSiteUsage[];
}
export const DialogDelete: React.FC<Props> = ({
  feature, name, onDelete, open, setOpen,
  trigger,
  title = "Are you absolutely sure?",
  description = "Unexpected bad things will happen if you don't read this!",
  confirmLabel,
  blockedBy,
}) => {
  const isBlocked = blockedBy && blockedBy.length > 0;
  const navigate = useNavigate();

  const defaultTrigger = (
    <GradientButton variant={"ghost"} size={"icon-sm"}><IconTrash className="size-5!" /></GradientButton>
  );

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <form>
        <DialogTrigger asChild>
          {trigger ?? defaultTrigger}
        </DialogTrigger>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{isBlocked ? "Cannot delete" : title}</DialogTitle>
            <DialogDescription>
              {isBlocked
                ? `The ${feature} "${name}" is in use and cannot be deleted.`
                : description}
            </DialogDescription>
          </DialogHeader>
          {isBlocked ? (
            <div className="space-y-3">
              <p className="text-sm">The following Semantic Navigation sites are using this {feature}:</p>
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
              <p className="text-sm text-muted-foreground">Remove this {feature} from those sites before deleting it.</p>
            </div>
          ) : (
            <p>This action cannot be undone. This will permanently delete <span className="font-semibold">{name}</span> {feature}.</p>
          )}
          <DialogFooter className="sm:justify-center">
            {isBlocked ? (
              <GradientButton onClick={() => setOpen(false)} variant="outline">Close</GradientButton>
            ) : (
              <GradientButton onClick={onDelete} variant="destructive">
                {confirmLabel ?? `I understand the consequences, delete this ${feature}`}
              </GradientButton>
            )}
          </DialogFooter>
        </DialogContent>
      </form>
    </Dialog>
  );
}
