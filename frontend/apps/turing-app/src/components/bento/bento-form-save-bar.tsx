import { BentoSaveBar, type BentoSaveBarProps } from "./bento-save-bar";
import { BentoScrollSaveBar } from "./bento-scroll-save-bar";

/**
 * The complete "hero → sticky save bar" morph as a **single drop-in** for Bento
 * form pages that render their own hero (i.e. not built on
 * {@link BentoEntityShell}) — persona editor, AI-agent sub-sections, SN forms,
 * admin forms, etc.
 *
 * Historically each such page hand-wired two coupled pieces with the *same*
 * props: an in-flow {@link BentoSaveBar} in a `bento-fade-out` wrapper (visible
 * at the top, fades away on scroll) and a fixed {@link BentoScrollSaveBar} that
 * fades in below the shell header. This component renders both from one set of
 * props so pages can't drift out of sync or forget half of it.
 *
 * Usage — render it **immediately after the hero, inside the `<form>`** it
 * submits (the default Save button is `type="submit"`, so no `formId` needed):
 *
 * ```tsx
 * <form onSubmit={handleSubmit(onSubmit)}>
 *   <MyHero … />
 *   <BentoFormSaveBar
 *     title={watchedTitle}
 *     onCancel={() => navigate(listRoute)}
 *     loading={isSubmitting}
 *     titleMissing={!watchedTitle.trim()}
 *     dirty={isNew || isDirty}
 *   />
 *   {/* sections … *​/}
 * </form>
 * ```
 *
 * Keep any destructive action in the hero (a `⋮` {@link BentoActionsMenu}),
 * never here — the save bar is Save/Cancel only.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export function BentoFormSaveBar(props: Readonly<BentoSaveBarProps>) {
  return (
    <>
      {/* In-flow bar at the top of the page; fades + lifts away as the hero
          scrolls off (reads the inherited `--bento-fade`). */}
      <div className="bento-fade-out">
        <BentoSaveBar {...props} />
      </div>
      {/* Fixed twin that fades in at top-20 once the hero is gone. Owns the
          scroll sentinel + layout spacer. */}
      <BentoScrollSaveBar {...props} />
    </>
  );
}
