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
}
export const DialogDelete: React.FC<Props> = ({
  feature, name, onDelete, open, setOpen,
  trigger,
  title = "Are you absolutely sure?",
  description = "Unexpected bad things will happen if you don't read this!",
  confirmLabel,
}) => {
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
            <DialogTitle>{title}</DialogTitle>
            <DialogDescription>{description}</DialogDescription>
          </DialogHeader>
          <p>This action cannot be undone. This will permanently delete <span className="font-semibold">{name}</span> {feature}.</p>
          <DialogFooter className="sm:justify-center">
            <GradientButton onClick={onDelete} variant="destructive">
              {confirmLabel ?? `I understand the consequences, delete this ${feature}`}
            </GradientButton>
          </DialogFooter>
        </DialogContent>
      </form>
    </Dialog>
  );
}
