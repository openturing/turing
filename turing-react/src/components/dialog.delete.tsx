import { IconTrash } from "@tabler/icons-react";
import React, { type Dispatch, type SetStateAction } from "react";
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
  blockedBy?: string[];
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
            <div className="space-y-2">
              <p className="text-sm">The following Semantic Navigation sites are using this {feature}:</p>
              <ul className="list-disc pl-5 space-y-1">
                {blockedBy.map((site) => (
                  <li key={site} className="text-sm font-semibold">{site}</li>
                ))}
              </ul>
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
