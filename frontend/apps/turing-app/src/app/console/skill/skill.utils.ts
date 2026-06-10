import type { TurSkill, TurSkillManifest } from "@/models/skill/skill.model";

/**
 * One marketplace/CRUD row keyed by skill `name`, merging the bundled catalog
 * entry (the installable version) with any installed versions of the same name.
 * Shared by the marketplace storefront (T118) and the skill detail dialog.
 *
 * @since 2026.3.1
 */
export interface SkillGroup {
    name: string;
    author: string | null;
    description: string | null;
    /** Bundled manifest, when this name exists in the platform catalog. */
    catalog: TurSkillManifest | null;
    /** Installed rows for this name, newest version first. */
    installed: TurSkill[];
    /** Distinct tool names contributed by the skill (from catalog or installed). */
    tools: string[];
    /** True when at least one installed row is a delete-protected platform skill. */
    builtIn: boolean;
}

/** Merge the catalog manifests and installed rows into one group per name. */
export function buildSkillGroups(catalog: TurSkillManifest[], installed: TurSkill[]): SkillGroup[] {
    const byName = new Map<string, SkillGroup>();

    const ensure = (name: string): SkillGroup => {
        let g = byName.get(name);
        if (!g) {
            g = { name, author: null, description: null, catalog: null, installed: [], tools: [], builtIn: false };
            byName.set(name, g);
        }
        return g;
    };

    for (const manifest of catalog) {
        const g = ensure(manifest.name);
        g.catalog = manifest;
        g.author = manifest.author ?? g.author;
        g.description = manifest.description ?? g.description;
        mergeTools(g, manifest.tools ?? []);
    }

    for (const skill of installed) {
        const g = ensure(skill.name);
        g.installed.push(skill);
        g.author = g.author ?? skill.author;
        g.description = g.description ?? skill.description;
        mergeTools(g, skill.tools ?? []);
    }

    for (const g of byName.values()) {
        // newest version first (numeric-aware so 1.10.0 > 1.9.0)
        g.installed.sort((a, b) => b.version.localeCompare(a.version, undefined, { numeric: true }));
        g.builtIn = g.installed.some((s) => s.builtIn) || (g.installed.length === 0 && g.catalog !== null);
    }

    return Array.from(byName.values()).sort((a, b) => a.name.localeCompare(b.name));
}

function mergeTools(group: SkillGroup, tools: string[]) {
    for (const tool of tools) {
        if (!group.tools.includes(tool)) group.tools.push(tool);
    }
}
