/**
 * Minimal ambient declaration for the OPTIONAL `@terrastruct/d2` peer engine.
 *
 * <p>{@link TuringD2Diagram} reaches the engine through a literal
 * {@code import("@terrastruct/d2")} so the host bundler code-splits it into its
 * own chunk and tree-shakes it out of apps that never render a ```d2 block
 * (roadmap T297). The package is an OPTIONAL peer dependency — it is not
 * installed in this library's own {@code node_modules} — so this declaration
 * lets {@code tsc} type-check the literal import without it. It mirrors only the
 * sliver of the real API we call ({@code new D2().compile()} → {@code render()}),
 * per the package README; the full upstream types ride along when the host app
 * actually installs the package.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
declare module "@terrastruct/d2" {
  export interface D2RenderOptions {
    /** Enable sketch (hand-drawn) mode. */
    sketch?: boolean;
    /** Theme ID; pair with {@link darkThemeID} for dark mode. */
    themeID?: number;
    darkThemeID?: number;
    pad?: number;
    scale?: number;
    [key: string]: unknown;
  }

  export interface D2CompileOptions extends D2RenderOptions {
    /** Layout engine. Default `"dagre"`. */
    layout?: "dagre" | "elk";
  }

  export interface D2CompileResponse {
    /** Compiled diagram model, fed back into {@link D2.render}. */
    diagram: unknown;
    /** Render options merged with config declared inside the diagram. */
    renderOptions: D2RenderOptions;
    [key: string]: unknown;
  }

  export class D2 {
    compile(
      input: string,
      options?: D2CompileOptions,
    ): Promise<D2CompileResponse>;
    render(diagram: unknown, options?: D2RenderOptions): Promise<string>;
  }
}
