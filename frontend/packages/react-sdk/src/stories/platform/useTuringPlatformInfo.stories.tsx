import type { Meta, StoryObj } from "@storybook/react-vite";

/**
 * # useTuringPlatformInfo
 *
 * The **capability-discovery** hook. A generic embed (widget, white-label
 * console, EDS block) rarely knows up-front *which* features the Turing
 * backend it connected to actually has switched on — storage-backed assets,
 * RAG chat, skills, multi-tenancy, which LLM vendors are configured, which
 * locales the system speaks. `useTuringPlatformInfo` answers that in one
 * shot so the UI can **feature-gate progressively**: render only what the
 * server supports, hide the rest, and stay forward-compatible as backends
 * are upgraded.
 *
 * It bundles two always-on reads — `GET /discovery` (identity + auth
 * topology) and `GET /features` (capability flags) — with two opt-in reads,
 * `GET /locale` and `GET /llm/vendor` (the vendor list is secured on some
 * deployments, so it fails open to an empty array).
 *
 * ## Key Features
 * - **`discovery`** — product name, `multiTenant`, `keycloak`,
 *   `authThirdparty`, `selfRegistration`, `oauth2Providers[]`
 * - **`features`** — the boolean capability matrix: `storageEnabled`,
 *   `ragEnabled`, `gitServerEnabled`, `marketplaceEnabled`, `skillsEnabled`,
 *   `tenancyEnabled`, `platformAdmin`, `seInstanceReadOnly`, plus the
 *   `loggingEngine` string
 * - **`locales`** — supported `TurSystemLocale[]` (`initials` + EN/PT labels)
 * - **`vendors`** — configured `TurLlmVendor[]` (id / title / plugin)
 * - **`loaded`** — flips true once the parallel fetch settles
 * - **`error`** — aggregated message from the discovery/features reads
 * - Optional reads gated by `includeLocales` / `includeVendors` options
 *
 * ## Usage
 * ```tsx
 * function App() {
 *   const { features, vendors, locales, loaded } = useTuringPlatformInfo();
 *
 *   if (!loaded) return <Spinner />;
 *
 *   return (
 *     <Shell>
 *       // Gate the whole chat surface on a server flag
 *       {features?.ragEnabled && <ChatButton vendors={vendors} />}
 *
 *       // Hide the assets tab when no storage backend is configured
 *       {features?.storageEnabled && <AssetsTab />}
 *
 *       // Only show skills when the backend has them switched on
 *       {features?.skillsEnabled && <SkillsPanel />}
 *
 *       // Offer a locale switcher only if more than one locale exists
 *       {locales.length > 1 && <LocalePicker locales={locales} />}
 *     </Shell>
 *   );
 * }
 * ```
 *
 * ## When to use
 *
 * - **Generic / white-label embeds** that target many backends and must
 *   self-configure instead of hard-coding which features exist
 * - **Progressive feature-gating** — never render a chat button against a
 *   backend with no LLM vendor, never show an assets tab with no storage
 * - **Forward compatibility** — when a backend upgrade flips a new flag on,
 *   the UI lights up the feature with no client release
 * - **Auth-aware shells** — read `discovery.keycloak` / `oauth2Providers`
 *   to render the right sign-in affordances
 *
 * ## About this story
 *
 * Fully self-contained: there is **no real hook, Provider, or network call**.
 * Each story passes an inline mock `PlatformInfo` object (a full-featured
 * SaaS backend, a stripped-down search-only backend, and a dark-themed
 * multi-tenant enterprise backend). The dashboard renders every capability
 * as an on/off pill so you can *see* what the gating logic above would
 * keep or hide for that configuration.
 */

// ---------------------------------------------------------------------------
// Local mirror of the hook's return surface (see `../../core/types` for the
// canonical TurDiscoveryInfo / TurFeaturesInfo / TurSystemLocale / TurLlmVendor).
// Re-declared locally to keep the story self-contained and TS-clean.
// ---------------------------------------------------------------------------

interface DiscoveryInfo {
  readonly product: string;
  readonly multiTenant: boolean;
  readonly keycloak: boolean;
  readonly authThirdparty: boolean;
  readonly selfRegistration: boolean;
  readonly oauth2Providers: string[];
}

interface FeaturesInfo {
  readonly storageEnabled: boolean;
  readonly ragEnabled: boolean;
  readonly gitServerEnabled: boolean;
  readonly marketplaceEnabled: boolean;
  readonly seInstanceReadOnly: boolean;
  readonly skillsEnabled: boolean;
  readonly tenancyEnabled: boolean;
  readonly platformAdmin: boolean;
  readonly loggingEngine: string;
}

interface SystemLocale {
  readonly initials: string;
  readonly en: string;
  readonly pt: string;
}

interface LlmVendor {
  readonly id: string;
  readonly title: string;
  readonly plugin?: string;
}

interface PlatformInfo {
  readonly discovery: DiscoveryInfo;
  readonly features: FeaturesInfo;
  readonly locales: SystemLocale[];
  readonly vendors: LlmVendor[];
}

const GRADIENT = "linear-gradient(135deg, #2563eb 0%, #4f46e5 100%)";

// ---------------------------------------------------------------------------
// Mock backends
// ---------------------------------------------------------------------------

const FULL_FEATURED: PlatformInfo = {
  discovery: {
    product: "Viglet Turing ES",
    multiTenant: false,
    keycloak: true,
    authThirdparty: true,
    selfRegistration: true,
    oauth2Providers: ["google", "github", "microsoft"],
  },
  features: {
    storageEnabled: true,
    ragEnabled: true,
    gitServerEnabled: true,
    marketplaceEnabled: true,
    seInstanceReadOnly: false,
    skillsEnabled: true,
    tenancyEnabled: false,
    platformAdmin: true,
    loggingEngine: "mongodb",
  },
  locales: [
    { initials: "en_US", en: "English", pt: "Inglês" },
    { initials: "pt_BR", en: "Portuguese", pt: "Português" },
    { initials: "es_ES", en: "Spanish", pt: "Espanhol" },
  ],
  vendors: [
    { id: "openai", title: "OpenAI", plugin: "openai" },
    { id: "anthropic", title: "Anthropic", plugin: "anthropic" },
    { id: "gemini", title: "Google Gemini", plugin: "gemini" },
    { id: "ollama", title: "Ollama", plugin: "ollama" },
  ],
};

const MINIMAL: PlatformInfo = {
  discovery: {
    product: "Viglet Turing ES",
    multiTenant: false,
    keycloak: false,
    authThirdparty: false,
    selfRegistration: false,
    oauth2Providers: [],
  },
  features: {
    storageEnabled: false,
    ragEnabled: false,
    gitServerEnabled: false,
    marketplaceEnabled: false,
    seInstanceReadOnly: true,
    skillsEnabled: false,
    tenancyEnabled: false,
    platformAdmin: false,
    loggingEngine: "none",
  },
  locales: [{ initials: "en_US", en: "English", pt: "Inglês" }],
  vendors: [],
};

const ENTERPRISE: PlatformInfo = {
  discovery: {
    product: "Viglet Turing ES",
    multiTenant: true,
    keycloak: true,
    authThirdparty: true,
    selfRegistration: false,
    oauth2Providers: ["azure-ad", "okta"],
  },
  features: {
    storageEnabled: true,
    ragEnabled: true,
    gitServerEnabled: false,
    marketplaceEnabled: false,
    seInstanceReadOnly: false,
    skillsEnabled: true,
    tenancyEnabled: true,
    platformAdmin: true,
    loggingEngine: "opensearch",
  },
  locales: [
    { initials: "en_US", en: "English", pt: "Inglês" },
    { initials: "pt_BR", en: "Portuguese", pt: "Português" },
  ],
  vendors: [
    { id: "azure-openai", title: "Azure OpenAI", plugin: "azure-openai" },
    { id: "anthropic", title: "Anthropic", plugin: "anthropic" },
  ],
};

// ---------------------------------------------------------------------------
// Dashboard
// ---------------------------------------------------------------------------

interface DashboardProps {
  readonly info: PlatformInfo;
  readonly dark?: boolean;
}

function Pill({ label, on, dark }: { label: string; on: boolean; dark: boolean }) {
  const onBg = "#dcfce7";
  const onFg = "#166534";
  const offBg = dark ? "#334155" : "#e2e8f0";
  const offFg = dark ? "#94a3b8" : "#64748b";
  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: "10px",
        padding: "9px 13px",
        borderRadius: "10px",
        background: on ? onBg : offBg,
        color: on ? onFg : offFg,
        fontSize: "13px",
        fontWeight: 600,
        border: on ? "1px solid #86efac" : `1px solid ${dark ? "#475569" : "#cbd5e1"}`,
      }}
    >
      <span>{label}</span>
      <span
        style={{
          fontSize: "11px",
          padding: "2px 8px",
          borderRadius: "999px",
          background: on ? "#16a34a" : (dark ? "#1e293b" : "#cbd5e1"),
          color: on ? "white" : offFg,
          letterSpacing: "0.04em",
        }}
      >
        {on ? "● ON" : "○ OFF"}
      </span>
    </div>
  );
}

function Chip({ children, dark }: { children: React.ReactNode; dark: boolean }) {
  return (
    <span
      style={{
        display: "inline-block",
        padding: "4px 11px",
        borderRadius: "999px",
        background: dark ? "#1e293b" : "#eef2ff",
        color: dark ? "#c7d2fe" : "#4338ca",
        fontSize: "12px",
        fontWeight: 600,
        border: `1px solid ${dark ? "#4f46e5" : "#c7d2fe"}`,
      }}
    >
      {children}
    </span>
  );
}

function SectionLabel({ children, dark }: { children: React.ReactNode; dark: boolean }) {
  return (
    <div
      style={{
        fontSize: "11px",
        fontWeight: 700,
        textTransform: "uppercase",
        letterSpacing: "0.07em",
        color: dark ? "#64748b" : "#94a3b8",
        margin: "18px 0 10px",
      }}
    >
      {children}
    </div>
  );
}

function PlatformDashboard({ info, dark = false }: DashboardProps) {
  const { discovery, features, locales, vendors } = info;
  const bg = dark ? "#0a0a0f" : "#ffffff";
  const card = dark ? "#16161f" : "#f8fafc";
  const border = dark ? "#1e1e2e" : "#e2e8f0";
  const text = dark ? "#e2e8f0" : "#0f172a";
  const muted = dark ? "#94a3b8" : "#64748b";

  // The gating decisions a real app would make from these flags.
  const showChat = features.ragEnabled && vendors.length > 0;
  const showAssets = features.storageEnabled;
  const showSkills = features.skillsEnabled;
  const showLocaleSwitcher = locales.length > 1;

  return (
    <div
      style={{
        fontFamily: "system-ui, sans-serif",
        width: "620px",
        background: bg,
        color: text,
        borderRadius: "16px",
        border: `1px solid ${border}`,
        overflow: "hidden",
      }}
    >
      {/* Header */}
      <div style={{ padding: "20px 24px", background: GRADIENT, color: "white" }}>
        <div style={{ fontSize: "12px", opacity: 0.85, letterSpacing: "0.05em" }}>
          PLATFORM DISCOVERY · GET /discovery + /features
        </div>
        <div style={{ fontSize: "22px", fontWeight: 800, marginTop: "4px" }}>
          {discovery.product}
        </div>
        <div style={{ display: "flex", gap: "8px", marginTop: "12px", flexWrap: "wrap" }}>
          <span style={badgeStyle}>{discovery.multiTenant ? "Multi-tenant" : "Single-tenant"}</span>
          <span style={badgeStyle}>{discovery.keycloak ? "Keycloak SSO" : "Basic auth"}</span>
          <span style={badgeStyle}>Logging: {features.loggingEngine}</span>
        </div>
      </div>

      <div style={{ padding: "16px 24px 24px" }}>
        {/* Capability matrix */}
        <SectionLabel dark={dark}>Capability flags (features)</SectionLabel>
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "1fr 1fr",
            gap: "8px",
          }}
        >
          <Pill label="RAG / Chat" on={features.ragEnabled} dark={dark} />
          <Pill label="Storage / Assets" on={features.storageEnabled} dark={dark} />
          <Pill label="Skills" on={features.skillsEnabled} dark={dark} />
          <Pill label="Git Server" on={features.gitServerEnabled} dark={dark} />
          <Pill label="Marketplace" on={features.marketplaceEnabled} dark={dark} />
          <Pill label="Multi-tenancy" on={features.tenancyEnabled} dark={dark} />
          <Pill label="Platform Admin" on={features.platformAdmin} dark={dark} />
          <Pill label="SE Read-only" on={features.seInstanceReadOnly} dark={dark} />
        </div>

        {/* Auth topology */}
        <SectionLabel dark={dark}>Auth topology (discovery)</SectionLabel>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "8px" }}>
          <Pill label="Keycloak" on={discovery.keycloak} dark={dark} />
          <Pill label="Third-party auth" on={discovery.authThirdparty} dark={dark} />
          <Pill label="Self-registration" on={discovery.selfRegistration} dark={dark} />
          <Pill label="OAuth2 providers" on={discovery.oauth2Providers.length > 0} dark={dark} />
        </div>
        {discovery.oauth2Providers.length > 0 && (
          <div style={{ display: "flex", gap: "6px", marginTop: "10px", flexWrap: "wrap" }}>
            {discovery.oauth2Providers.map((p) => (
              <Chip key={p} dark={dark}>{p}</Chip>
            ))}
          </div>
        )}

        {/* Vendors */}
        <SectionLabel dark={dark}>LLM vendors ({vendors.length})</SectionLabel>
        {vendors.length > 0 ? (
          <div style={{ display: "flex", gap: "6px", flexWrap: "wrap" }}>
            {vendors.map((v) => (
              <Chip key={v.id} dark={dark}>{v.title}</Chip>
            ))}
          </div>
        ) : (
          <div style={{ fontSize: "13px", color: muted, fontStyle: "italic" }}>
            No vendors configured — or the /llm/vendor read is secured on this backend.
          </div>
        )}

        {/* Locales */}
        <SectionLabel dark={dark}>System locales ({locales.length})</SectionLabel>
        <div style={{ display: "flex", gap: "6px", flexWrap: "wrap" }}>
          {locales.map((l) => (
            <Chip key={l.initials} dark={dark}>{`${l.initials} · ${l.en}`}</Chip>
          ))}
        </div>

        {/* What the UI gates ON */}
        <SectionLabel dark={dark}>What this UI would render</SectionLabel>
        <div
          style={{
            background: card,
            border: `1px solid ${border}`,
            borderRadius: "12px",
            padding: "14px 16px",
            fontSize: "13px",
            lineHeight: 1.7,
          }}
        >
          <GateLine on={showChat} dark={dark}>
            Chat button — <code>features.ragEnabled &amp;&amp; vendors.length &gt; 0</code>
          </GateLine>
          <GateLine on={showAssets} dark={dark}>
            Assets tab — <code>features.storageEnabled</code>
          </GateLine>
          <GateLine on={showSkills} dark={dark}>
            Skills panel — <code>features.skillsEnabled</code>
          </GateLine>
          <GateLine on={showLocaleSwitcher} dark={dark}>
            Locale switcher — <code>locales.length &gt; 1</code>
          </GateLine>
        </div>
      </div>
    </div>
  );
}

const badgeStyle: React.CSSProperties = {
  fontSize: "11px",
  fontWeight: 600,
  padding: "3px 10px",
  borderRadius: "999px",
  background: "rgba(255,255,255,0.18)",
  color: "white",
};

function GateLine({
  on,
  dark,
  children,
}: {
  on: boolean;
  dark: boolean;
  children: React.ReactNode;
}) {
  return (
    <div style={{ display: "flex", alignItems: "baseline", gap: "8px" }}>
      <span
        style={{
          color: on ? "#16a34a" : (dark ? "#64748b" : "#94a3b8"),
          fontWeight: 700,
          minWidth: "62px",
        }}
      >
        {on ? "✓ shown" : "✗ hidden"}
      </span>
      <span style={{ color: on ? undefined : (dark ? "#64748b" : "#94a3b8") }}>{children}</span>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Meta + stories
// ---------------------------------------------------------------------------

const meta: Meta<typeof PlatformDashboard> = {
  title: "Platform/useTuringPlatformInfo",
  component: PlatformDashboard,
  tags: ["autodocs"],
  parameters: {
    layout: "centered",
    docs: {
      description: {
        component:
          "Capabilities dashboard for `useTuringPlatformInfo` — the discovery hook that bundles `GET /discovery` + `/features` (+ optional `/locale` and `/llm/vendor`) so a generic embed can self-configure. Each story renders an inline mock backend (no real hook/network) as a grid of on/off capability pills, then shows which UI surfaces the standard gating logic would keep or hide for that configuration.",
      },
    },
  },
  argTypes: {
    info: {
      description: "Mock platform-info object (discovery + features + locales + vendors).",
      control: false,
    },
    dark: {
      description: "Render the dashboard on the Viglet dark surface (#0a0a0f).",
      control: { type: "boolean" },
    },
  },
};

export default meta;
type Story = StoryObj<typeof PlatformDashboard>;

export const FullFeatured: Story = {
  name: "🛠️ Full-featured SaaS backend (everything on)",
  args: { info: FULL_FEATURED, dark: false },
};

export const Minimal: Story = {
  name: "📋 Search-only backend (chat / storage / skills off)",
  args: { info: MINIMAL, dark: false },
};

export const Enterprise: Story = {
  name: "🛠️ Multi-tenant enterprise backend (dark)",
  args: { info: ENTERPRISE, dark: true },
};
