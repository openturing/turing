/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.persona.match;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

import com.viglet.turing.genai.persona.fit.TurContentFitMisfit;
import com.viglet.turing.genai.persona.fit.TurPersonaVerdictHealth;
import com.viglet.turing.genai.persona.match.dto.TurPersonaMatchAdjustmentDto;
import com.viglet.turing.genai.persona.match.dto.TurPersonaMatchAdjustmentDto.Item;
import com.viglet.turing.genai.persona.match.dto.TurPersonaMatchAdjustmentDto.Target;
import com.viglet.turing.genai.persona.match.dto.TurPersonaMatchAdjustmentVerdict;
import com.viglet.turing.genai.persona.match.dto.TurPersonaMatchDriftDto;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.persona.match.TurPersonaMatchProject;
import com.viglet.turing.persistence.model.persona.match.TurPersonaMatchSource;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
import com.viglet.turing.persistence.repository.persona.match.TurPersonaMatchPersonaRepository;
import com.viglet.turing.persistence.repository.persona.match.TurPersonaMatchProjectRepository;
import com.viglet.turing.persistence.repository.persona.match.TurPersonaMatchSourceRepository;
import com.viglet.turing.system.TurLlmSummaryService;
import com.viglet.turing.system.TurLlmSummaryService.StructuredResult;
import com.viglet.turing.system.TurLlmSummaryService.StructuredSpec;
import com.viglet.turing.system.TurVerdictSource;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * LLM-suggested persona/content adjustments for a Persona Match project
 * (Block AT / §XLIII, T704) — the payoff half of the tuning loop. The matrix says
 * <em>where</em> fit is weak and T704's drift says <em>what moved</em>; this
 * service says <em>what to change</em>, on both sides: edit the content so it
 * serves the persona, or edit the persona so it describes the audience the
 * content actually reaches.
 *
 * <p>Grounded by construction. The prompt only ever sees scores, the evaluator's
 * own summaries and the already-grounded misfit spans (T385 hard-fail applied at
 * evaluation time), and any suggestion naming a target that is not in the project
 * is dropped on parse. When no LLM is reachable — or its output cannot be parsed
 * — the report degrades to a <strong>deterministic</strong> one assembled from the
 * persisted misfit suggestions and the weakest pairs, so the surface is never
 * empty and the whole feature stays testable without a network.
 *
 * <p>Advisory only: nothing here writes to a persona or a content.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurPersonaMatchAdjustmentService {

    /** Targets suggested per side — the weakest ones, so the list stays actionable. */
    private static final int MAX_TARGETS_PER_SIDE = 5;
    /** Deterministic fallback: actions per target. */
    private static final int MAX_FALLBACK_ACTIONS = 3;
    /** Misfit spans fed to the prompt per cell (the prompt must stay small). */
    private static final int MAX_MISFITS_PER_CELL = 3;

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    /** T829 — the verdict shape as a provider-enforceable schema, derived once. */
    private static final BeanOutputConverter<TurPersonaMatchAdjustmentVerdict> VERDICT_CONVERTER =
            new BeanOutputConverter<>(TurPersonaMatchAdjustmentVerdict.class);
    private static final String VERDICT_SCHEMA_NAME = "persona_match_adjustments";

    private final TurPersonaMatchProjectRepository projectRepository;
    private final TurPersonaMatchSourceRepository sourceRepository;
    private final TurPersonaMatchPersonaRepository personaJoinRepository;
    private final TurPersonaRepository personaRepository;
    private final TurPersonaMatchAnalysisService analysisService;
    private final TurPersonaMatchHistoryService historyService;
    private final TurLlmSummaryService llmSummaryService;
    private final TurPersonaVerdictHealth verdictHealth;

    public TurPersonaMatchAdjustmentService(TurPersonaMatchProjectRepository projectRepository,
            TurPersonaMatchSourceRepository sourceRepository,
            TurPersonaMatchPersonaRepository personaJoinRepository,
            TurPersonaRepository personaRepository,
            TurPersonaMatchAnalysisService analysisService,
            TurPersonaMatchHistoryService historyService,
            TurLlmSummaryService llmSummaryService,
            TurPersonaVerdictHealth verdictHealth) {
        this.projectRepository = projectRepository;
        this.sourceRepository = sourceRepository;
        this.personaJoinRepository = personaJoinRepository;
        this.personaRepository = personaRepository;
        this.analysisService = analysisService;
        this.historyService = historyService;
        this.llmSummaryService = llmSummaryService;
        this.verdictHealth = verdictHealth;
    }

    /**
     * Suggest adjustments for a project from its persisted matrix + drift.
     *
     * @param regenerate bypass the LLM cache for a fresh set of suggestions
     */
    public TurPersonaMatchAdjustmentDto suggest(String projectId, boolean regenerate) {
        TurPersonaMatchProject project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            return empty(projectId, "Project not found.");
        }

        List<TurPersonaMatchCellDto> cells = analysisService.getMatrix(projectId).cells();
        if (cells.isEmpty()) {
            return empty(projectId, "Run the analysis first — there is no matrix to tune from.");
        }

        Context context = buildContext(projectId, cells);
        if (context.contents().isEmpty() || context.personas().isEmpty()) {
            return empty(projectId, "Project has no content/persona pair to tune.");
        }

        // T829: the verdict shape is enforced by the provider where one offers it; the
        // prompt-JSON contract is now only the fallback for providers without one.
        StructuredResult llm = llmSummaryService.generateStructured(cacheKey(project, context),
                context.digest(), systemPrompt(), regenerate, project.getLlmInstanceId(),
                verdictSpec());
        if (llm.success() && StringUtils.isNotBlank(llm.content())) {
            List<Item> items = parse(llm.content(), context);
            if (!items.isEmpty()) {
                verdictHealth.record(llm.source());
                return new TurPersonaMatchAdjustmentDto(projectId, true, null, Instant.now(),
                        items);
            }
            // The model answered and nothing usable came out of it — counted as a
            // distinct degradation, not lumped in with "no LLM configured".
            verdictHealth.record(TurVerdictSource.UNREADABLE);
            log.debug("[PersonaMatch] adjustment verdict unusable — falling back to "
                    + "deterministic suggestions for project={}", projectId);
            return new TurPersonaMatchAdjustmentDto(projectId, false,
                    "Could not parse the LLM adjustment verdict.", Instant.now(),
                    fallback(context));
        }
        verdictHealth.record(TurVerdictSource.UNAVAILABLE);
        return new TurPersonaMatchAdjustmentDto(projectId, false, llm.error(), Instant.now(),
                fallback(context));
    }

    /**
     * The schema this service binds the verdict to. The converter's format
     * instruction rides along for the plain-chat fallback only.
     */
    private static StructuredSpec verdictSpec() {
        return new StructuredSpec(VERDICT_SCHEMA_NAME, verdictSchema(),
                VERDICT_CONVERTER.getFormat());
    }

    private static Map<String, Object> verdictSchema() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> schema = MAPPER.readValue(VERDICT_CONVERTER.getJsonSchema(),
                    Map.class);
            return schema;
        } catch (RuntimeException e) {
            log.debug("[PersonaMatch] could not derive the adjustment JSON schema: {}",
                    e.getMessage());
            return Map.of();
        }
    }

    // ---- context -----------------------------------------------------------

    private Context buildContext(String projectId, List<TurPersonaMatchCellDto> cells) {
        Map<String, String> sourceNames = new HashMap<>();
        for (TurPersonaMatchSource s : sourceRepository
                .findByProject_IdOrderBySourceNameAsc(projectId)) {
            sourceNames.put(s.getId(), StringUtils.defaultIfBlank(s.getSourceName(), s.getRef()));
        }
        Map<String, String> personaNames = new HashMap<>();
        Map<String, String> personaDescriptions = new HashMap<>();
        personaJoinRepository.findByProject_Id(projectId).forEach(join -> {
            Optional<TurPersona> persona = personaRepository.findById(join.getPersonaId());
            persona.ifPresent(p -> {
                personaNames.put(p.getId(), p.getName());
                personaDescriptions.put(p.getId(), StringUtils.defaultString(p.getDescription()));
            });
        });

        // Only cells whose both sides still belong to the project.
        List<TurPersonaMatchCellDto> live = cells.stream()
                .filter(c -> sourceNames.containsKey(c.sourceId())
                        && personaNames.containsKey(c.personaId()))
                .toList();

        Map<String, Double> contentDelta = new HashMap<>();
        Map<String, Double> personaDelta = new HashMap<>();
        TurPersonaMatchDriftDto drift = historyService.drift(projectId);
        averageDeltas(drift, contentDelta, personaDelta);

        List<TargetStat> contents = statsBy(live, TurPersonaMatchCellDto::sourceId, sourceNames,
                contentDelta);
        List<TargetStat> personas = statsBy(live, TurPersonaMatchCellDto::personaId, personaNames,
                personaDelta);

        return new Context(live, sourceNames, personaNames, personaDescriptions, contents,
                personas);
    }

    /** Average each side's per-cell drift deltas into one movement per target. */
    private void averageDeltas(TurPersonaMatchDriftDto drift, Map<String, Double> contentDelta,
            Map<String, Double> personaDelta) {
        Map<String, List<Double>> perContent = new HashMap<>();
        Map<String, List<Double>> perPersona = new HashMap<>();
        for (TurPersonaMatchDriftDto.Entry e : drift.entries()) {
            perContent.computeIfAbsent(e.sourceId(), k -> new ArrayList<>()).add(e.delta());
            perPersona.computeIfAbsent(e.personaId(), k -> new ArrayList<>()).add(e.delta());
        }
        perContent.forEach((id, deltas) -> contentDelta.put(id, mean(deltas)));
        perPersona.forEach((id, deltas) -> personaDelta.put(id, mean(deltas)));
    }

    private List<TargetStat> statsBy(List<TurPersonaMatchCellDto> cells,
            java.util.function.Function<TurPersonaMatchCellDto, String> idOf,
            Map<String, String> names, Map<String, Double> deltas) {
        Map<String, List<TurPersonaMatchCellDto>> grouped = new HashMap<>();
        for (TurPersonaMatchCellDto c : cells) {
            grouped.computeIfAbsent(idOf.apply(c), k -> new ArrayList<>()).add(c);
        }
        return grouped.entrySet().stream()
                .map(e -> new TargetStat(e.getKey(), names.get(e.getKey()),
                        round(e.getValue().stream()
                                .mapToDouble(TurPersonaMatchCellDto::fitScore).average()
                                .orElse(0)),
                        deltas.get(e.getKey()), e.getValue()))
                // Weakest fit first — that is where a tuning suggestion pays off.
                .sorted(Comparator.comparingDouble(TargetStat::avgFit)
                        .thenComparing(s -> StringUtils.defaultString(s.name())))
                .toList();
    }

    // ---- prompt ------------------------------------------------------------

    private String cacheKey(TurPersonaMatchProject project, Context context) {
        // The digest carries every score/summary the verdict depends on, so hashing
        // it makes an unchanged matrix a cache hit and any re-run a miss.
        return "persona-match-adjust:" + project.getId() + ":"
                + StringUtils.defaultString(project.getLlmInstanceId(), "default") + ":"
                + Integer.toHexString(context.digest().hashCode());
    }

    private String systemPrompt() {
        return """
                You tune a content/audience portfolio. You receive a fit matrix:
                each CONTENT scored (0-100) against each AUDIENCE PERSONA, with the
                evaluator's summary, the verbatim phrases it flagged, and how each
                score MOVED since the previous run (delta).

                Propose adjustments on BOTH sides of the loop:
                - CONTENT items: how to edit that content so it serves the personas
                  that score it low.
                - PERSONA items: how to correct that persona's audience description
                  so it matches the readers the contents actually reach (e.g. the
                  reading level or vocabulary ceiling is set wrong).

                Return EXACTLY ONE JSON object on a single line — no prose, no code
                fences. Schema:
                {"items": [{"target": "CONTENT|PERSONA",
                            "targetId": "<an id copied verbatim from the input>",
                            "rationale": "<one sentence, cite the score or the delta>",
                            "actions": ["<one concrete edit>", "..."]}]}

                HARD RULES:
                - "targetId" MUST be copied verbatim from the input. Never invent an id.
                - Prioritise the lowest scores and the sharpest negative deltas.
                - Each action must be a concrete edit someone can carry out, not
                  advice like "improve the text".
                - At most %d CONTENT items and %d PERSONA items, at most 3 actions each.
                - Reply in the language the summaries are written in.
                """.formatted(MAX_TARGETS_PER_SIDE, MAX_TARGETS_PER_SIDE);
    }

    // ---- parsing -----------------------------------------------------------

    private List<Item> parse(String raw, Context context) {
        TurPersonaMatchAdjustmentVerdict verdict = readVerdict(raw);
        if (verdict == null || verdict.items() == null) {
            return List.of();
        }
        List<Item> items = new ArrayList<>();
        int contentCount = 0;
        int personaCount = 0;
        for (TurPersonaMatchAdjustmentVerdict.Item dto : verdict.items()) {
            Item item = toItem(dto, context);
            if (item == null) {
                continue;
            }
            if (item.target() == Target.CONTENT && contentCount++ >= MAX_TARGETS_PER_SIDE) {
                continue;
            }
            if (item.target() == Target.PERSONA && personaCount++ >= MAX_TARGETS_PER_SIDE) {
                continue;
            }
            items.add(item);
        }
        return items;
    }

    /** Null for anything ungrounded — an unknown target id or no actionable action. */
    private Item toItem(TurPersonaMatchAdjustmentVerdict.Item dto, Context context) {
        if (dto == null || StringUtils.isBlank(dto.targetId())) {
            return null;
        }
        Target target = parseTarget(dto.target());
        if (target == null) {
            return null;
        }
        String id = dto.targetId().strip();
        TargetStat stat = context.find(target, id);
        if (stat == null) {
            log.debug("[PersonaMatch] dropping adjustment for unknown {} id: {}", target, id);
            return null;
        }
        List<String> actions = (dto.actions() == null ? List.<String>of() : dto.actions())
                .stream().filter(StringUtils::isNotBlank).map(String::strip).limit(3).toList();
        if (actions.isEmpty()) {
            return null;
        }
        return new Item(target, id, stat.name(), stat.avgFit(), stat.delta(),
                StringUtils.trimToNull(dto.rationale()), actions);
    }

    private Target parseTarget(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        try {
            return Target.valueOf(raw.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Decodes the verdict. With the T829 provider schema in play this is a clean
     * JSON object; the brace-scan still rescues the prompt-JSON fallback rather than
     * being the only path.
     */
    private TurPersonaMatchAdjustmentVerdict readVerdict(String raw) {
        int start = raw == null ? -1 : raw.indexOf('{');
        int end = raw == null ? -1 : raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return MAPPER.readValue(raw.substring(start, end + 1),
                    TurPersonaMatchAdjustmentVerdict.class);
        } catch (RuntimeException e) {
            log.warn("[PersonaMatch] could not parse adjustment verdict: {}", e.getMessage());
            return null;
        }
    }

    // ---- deterministic fallback --------------------------------------------

    /**
     * The no-LLM report: for the weakest contents, the evaluator's own grounded
     * rewrite suggestions; for the weakest personas, which content misses them and
     * by how much. Less nuanced than the LLM path, never empty, never invented.
     */
    private List<Item> fallback(Context context) {
        List<Item> items = new ArrayList<>();
        for (TargetStat stat : context.contents().stream().limit(MAX_TARGETS_PER_SIDE).toList()) {
            items.add(new Item(Target.CONTENT, stat.id(), stat.name(), stat.avgFit(),
                    stat.delta(), contentRationale(stat, context), contentActions(stat)));
        }
        for (TargetStat stat : context.personas().stream().limit(MAX_TARGETS_PER_SIDE).toList()) {
            items.add(new Item(Target.PERSONA, stat.id(), stat.name(), stat.avgFit(),
                    stat.delta(), personaRationale(stat, context), personaActions(stat, context)));
        }
        return items;
    }

    private String contentRationale(TargetStat stat, Context context) {
        TurPersonaMatchCellDto worst = weakest(stat);
        String persona = worst == null ? null : context.personaNames().get(worst.personaId());
        if (persona == null) {
            return "Average fit %s%% across the matrix.".formatted(stat.avgFit());
        }
        return "Average fit %s%%; weakest against \"%s\" (%s%%).".formatted(stat.avgFit(),
                persona, round(worst.fitScore()));
    }

    private List<String> contentActions(TargetStat stat) {
        // The evaluator already grounded every suggestion in a verbatim span.
        Set<String> actions = new LinkedHashSet<>();
        for (TurPersonaMatchCellDto cell : sortedByFit(stat)) {
            for (TurContentFitMisfit misfit : cell.misfits()) {
                if (StringUtils.isNotBlank(misfit.suggestion())) {
                    actions.add("Rewrite \"%s\" — %s".formatted(
                            StringUtils.abbreviate(misfit.span(), 80), misfit.suggestion()));
                }
                if (actions.size() >= MAX_FALLBACK_ACTIONS) {
                    return List.copyOf(actions);
                }
            }
        }
        if (actions.isEmpty()) {
            actions.add("No phrase was flagged — the gap is structural: shorten sentences "
                    + "and add the context this audience is missing.");
        }
        return List.copyOf(actions);
    }

    private String personaRationale(TargetStat stat, Context context) {
        TurPersonaMatchCellDto worst = weakest(stat);
        String content = worst == null ? null : context.sourceNames().get(worst.sourceId());
        if (content == null) {
            return "Contents average %s%% fit for this persona.".formatted(stat.avgFit());
        }
        return "Contents average %s%% fit for this persona; \"%s\" fits worst (%s%%)."
                .formatted(stat.avgFit(), content, round(worst.fitScore()));
    }

    private List<String> personaActions(TargetStat stat, Context context) {
        List<String> actions = new ArrayList<>();
        actions.add("Review this persona's reading level and vocabulary ceiling — the "
                + "portfolio consistently scores %s%% for it.".formatted(stat.avgFit()));
        TurPersonaMatchCellDto worst = weakest(stat);
        if (worst != null && StringUtils.isNotBlank(worst.summary())) {
            actions.add("Check whether the audience description matches this verdict: "
                    + StringUtils.abbreviate(worst.summary(), 200));
        }
        if (StringUtils.isBlank(context.personaDescriptions().get(stat.id()))) {
            actions.add("This persona has no description — the evaluator is judging against "
                    + "a general reader.");
        }
        return actions;
    }

    private List<TurPersonaMatchCellDto> sortedByFit(TargetStat stat) {
        return stat.cells().stream()
                .sorted(Comparator.comparingDouble(TurPersonaMatchCellDto::fitScore))
                .toList();
    }

    private TurPersonaMatchCellDto weakest(TargetStat stat) {
        return sortedByFit(stat).stream().findFirst().orElse(null);
    }

    // ---- helpers -----------------------------------------------------------

    private TurPersonaMatchAdjustmentDto empty(String projectId, String error) {
        return new TurPersonaMatchAdjustmentDto(projectId, false, error, Instant.now(),
                List.of());
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static double mean(List<Double> values) {
        return round(values.stream().mapToDouble(Double::doubleValue).average().orElse(0));
    }

    /** One side of the loop, aggregated: a content or a persona with its cells. */
    private record TargetStat(String id, String name, double avgFit, Double delta,
            List<TurPersonaMatchCellDto> cells) {
    }

    /** Everything the prompt and the fallback read, resolved once. */
    private record Context(List<TurPersonaMatchCellDto> cells, Map<String, String> sourceNames,
            Map<String, String> personaNames, Map<String, String> personaDescriptions,
            List<TargetStat> contents, List<TargetStat> personas) {

        TargetStat find(Target target, String id) {
            List<TargetStat> pool = target == Target.CONTENT ? contents : personas;
            return pool.stream().filter(s -> s.id().equals(id)).findFirst().orElse(null);
        }

        /** The user-message payload: the matrix as compact, groundable lines. */
        String digest() {
            StringBuilder sb = new StringBuilder("CONTENTS:\n");
            sourceNames.forEach((id, name) ->
                    sb.append("- id=").append(id).append(" name=").append(name).append('\n'));
            sb.append("\nPERSONAS:\n");
            personaNames.forEach((id, name) -> {
                sb.append("- id=").append(id).append(" name=").append(name);
                String description = personaDescriptions.get(id);
                if (StringUtils.isNotBlank(description)) {
                    sb.append(" description=").append(StringUtils.abbreviate(description, 300));
                }
                sb.append('\n');
            });
            sb.append("\nMATRIX:\n");
            for (TurPersonaMatchCellDto cell : cells) {
                appendCell(sb, cell);
            }
            appendMovement(sb);
            return sb.toString();
        }

        private void appendCell(StringBuilder sb, TurPersonaMatchCellDto cell) {
            sb.append("- content=").append(cell.sourceId())
                    .append(" persona=").append(cell.personaId())
                    .append(" fit=").append(round(cell.fitScore()))
                    .append(" readability=").append(round(cell.readability()));
            if (StringUtils.isNotBlank(cell.summary())) {
                sb.append(" verdict=").append(StringUtils.abbreviate(cell.summary(), 300));
            }
            cell.misfits().stream().limit(MAX_MISFITS_PER_CELL)
                    .forEach(m -> sb.append("\n    flagged=\"")
                            .append(StringUtils.abbreviate(m.span(), 120)).append("\" reason=")
                            .append(m.reason()));
            sb.append('\n');
        }

        private void appendMovement(StringBuilder sb) {
            List<TargetStat> moved = new ArrayList<>(contents);
            moved.addAll(personas);
            boolean any = moved.stream().anyMatch(s -> s.delta() != null);
            if (!any) {
                sb.append("\nMOVEMENT: first run on record — no deltas yet.\n");
                return;
            }
            sb.append("\nMOVEMENT SINCE PREVIOUS RUN (average delta in fit points):\n");
            for (TargetStat stat : moved) {
                if (stat.delta() != null) {
                    sb.append("- id=").append(stat.id()).append(" delta=")
                            .append(stat.delta()).append('\n');
                }
            }
        }
    }

}
