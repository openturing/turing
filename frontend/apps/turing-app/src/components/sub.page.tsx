// T1008 -- a shim now, not a copy.
//
// This file carried a fork of the design system's SubPage because the package's
// copy was narrower in three ways and differed in a fourth: it had no
// outletContext, its NavMainItem required a url, it had no showOnNew, and its
// spacing was the roomier console default. VDS76 closed all four -- the first
// three additively, the last as a `density` prop whose default is the package's
// own spacing, so no other console moved.
//
// This product renders the tighter one, which is what the local copy always
// did, so the density is bound here rather than at the single call site: it is
// a property of this console's shell, not of the persona editor that happens to
// be the only page mounting it.
import { SubPage as SharedSubPage } from "@viglet/viglet-design-system/router";

// viglet-ds-allow-duplicate SubPage
// The name is deliberately re-declared: this is the adapter that binds this
// product's density, not a second implementation.
export type SubPageProps = Omit<
  React.ComponentProps<typeof SharedSubPage>,
  "density"
>;

export const SubPage: React.FC<SubPageProps> = (props) => (
  <SharedSubPage density="compact" {...props} />
);
