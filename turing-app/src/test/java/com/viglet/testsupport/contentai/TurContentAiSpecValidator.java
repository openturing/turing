/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.testsupport.contentai;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.OpenApi30;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * T1048 / §XX.25 — validates a Content AI request body against Adobe's own
 * published OpenAPI, so a body Adobe would reject fails the test that produced it.
 *
 * <p>Why this exists: the Content AI tests stand Adobe's endpoint in miniature, and
 * the stub replies {@code 200} to anything — so a test caught only what its author
 * thought to assert. T1031 shipped a {@code match_all} built as an empty
 * {@code FilterGroup}, which {@code minItems: 1} forbids, and it survived T1044,
 * T1045 and T1046 before the spec was re-read. Encoding the constraints somebody
 * happens to remember would repeat that mistake one level up, so this validates
 * against the whole contract instead.
 *
 * <p>T1050 — it validates the <b>reply</b> too. A stub answering with a shape its
 * own author invented proves the mapper agrees with itself, and the failure that
 * hides is quieter than a rejected request: a field read under the wrong name is
 * zero hits, an empty facet or a cursor that never advances, with no exception and
 * no log line. Pointing {@link #SEARCH_RESPONSE} at every canned response found two
 * fixtures short of what Adobe sends, and one contradiction in the bundle itself —
 * see {@link #reconcileBucketArray}.
 *
 * <p>The spec is <b>vendored and pinned</b> at
 * {@code src/test/resources/contentai/openapi.yaml} ({@code 2026.07-experimental}).
 * That is deliberate: fetching it at test time would make the build depend on
 * Adobe's uptime and let a remote change break an unrelated commit. The cost is that
 * refreshing it is a decision somebody makes — which is the point, because the API
 * is experimental and dated, so a diff against a newer bundle is a thing to read
 * rather than absorb.
 *
 * <h3>The one thing that makes it work</h3>
 *
 * <p>Adobe's schemas use the OpenAPI {@code discriminator} + {@code allOf}
 * inheritance pattern: {@code FilterComponent} declares only {@code type} plus a
 * {@code mapping}, and each concrete filter carries {@code allOf: [FilterComponent]}.
 * A validator handed that gets nowhere. There is no {@code oneOf} for the
 * discriminator to select from, so validating a {@code FilterGroup} instance against
 * {@code FilterComponent} checks that {@code type} is a string and descends no
 * further — {@code minItems} is never reached, and this class would pass the very
 * body it exists to catch. That was measured, not assumed: before
 * {@link #flattenInheritance} the empty group produced <em>zero</em> violations.
 *
 * <p>So the document is transformed on load, in the one way that keeps this honest —
 * <b>from the spec's own {@code discriminator.mapping}</b>, never from a list
 * somebody typed. Each discriminated parent becomes a {@code oneOf} over its mapped
 * children; each child absorbs the parent's own constraints in place of the
 * back-reference (which also breaks the cycle that would otherwise recurse), and
 * gains an {@code enum} pinning its discriminator value so exactly one branch can
 * match. The leaf constraints then fire: the empty group produces 114 violations.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurContentAiSpecValidator {

    /** The vendored bundle. Refreshing it is a deliberate act — see the class javadoc. */
    public static final String SPEC_RESOURCE = "/contentai/openapi.yaml";

    /** The schema a {@code POST /content-sources/search} body must satisfy. */
    public static final String SEARCH_REQUEST = "ContentSourceSearchRequest";

    /**
     * T1050 — the schema a {@code POST /content-sources/search} <em>reply</em>
     * must satisfy, so a canned fixture is a contract rather than a shape its own
     * author invented.
     */
    public static final String SEARCH_RESPONSE = "ContentSourceSearchResponse";

    /**
     * T1052 — the schema a {@code GET /content-sources} page must satisfy, which is
     * the second endpoint this suite stands in miniature.
     */
    public static final String SOURCE_LIST = "ContentSourceListPage";

    /** How many violations a failure message lists before summarising the rest. */
    private static final int REPORTED_VIOLATIONS = 12;

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final ObjectMapper YAML = YAMLMapper.builder().build();

    private static final JsonNode SPEC = loadSpec();
    private static final SchemaRegistry REGISTRY =
            SchemaRegistry.withDefaultDialect(OpenApi30.getInstance());

    private TurContentAiSpecValidator() {
    }

    private static JsonNode loadSpec() {
        try (InputStream in = TurContentAiSpecValidator.class
                .getResourceAsStream(SPEC_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(
                        "Vendored Content AI spec not found at " + SPEC_RESOURCE);
            }
            ObjectNode document = (ObjectNode) YAML.readTree(in);
            ObjectNode schemas = (ObjectNode) document.at("/components/schemas");
            reconcileBucketArray(schemas);
            flattenInheritance(schemas);
            return document;
        } catch (IOException e) {
            throw new IllegalStateException("Content AI spec could not be read", e);
        }
    }

    /**
     * The one place the bundle contradicts itself, corrected in the one way that
     * states the contradiction instead of erasing it.
     *
     * <p>{@code TermBucketResultData} declares {@code required: [values]} while the
     * only bucket property it defines is {@code buckets}. Both cannot be right, and
     * Adobe publishes no example response for aggregates to settle it. Reading it
     * literally would refuse every aggregate response Turing has ever seen —
     * {@code TurContentAiResponseMapper} accepts either key precisely because that
     * disagreement was found before this validator existed.
     *
     * <p>So the requirement becomes "one of the two" rather than being dropped: a
     * response carrying neither is still refused, which is the part that has value,
     * and a response carrying either passes. This is the only hand-authored
     * departure from the bundle, and it is narrow on purpose — one field of one
     * schema, asserted by its own test, so if Adobe reconciles the two this becomes
     * visible rather than silently redundant.
     */
    private static void reconcileBucketArray(ObjectNode schemas) {
        JsonNode termBuckets = schemas.get("TermBucketResultData");
        if (termBuckets == null || !termBuckets.has("required")) {
            throw new IllegalStateException(
                    "TermBucketResultData no longer declares a required bucket array; "
                            + "re-read the bundle before keeping this exception");
        }
        ObjectNode node = (ObjectNode) termBuckets;
        node.remove("required");
        ArrayNode anyOf = schemas.arrayNode();
        for (String key : List.of("values", "buckets")) {
            anyOf.add(schemas.objectNode()
                    .set("required", schemas.arrayNode().add(key)));
        }
        node.set("anyOf", anyOf);
    }

    /**
     * Rewrites OpenAPI {@code discriminator} inheritance into {@code oneOf}, so a
     * JSON Schema validator can descend into the concrete type. See the class
     * javadoc for why this is necessary; what matters here is that every input comes
     * from the document — the parents are found by having a
     * {@code discriminator.mapping}, and the children are the refs that mapping
     * names.
     */
    private static void flattenInheritance(ObjectNode schemas) {
        List<String> parents = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : schemas.properties()) {
            JsonNode discriminator = entry.getValue().get("discriminator");
            if (discriminator != null && discriminator.has("mapping")) {
                parents.add(entry.getKey());
            }
        }
        for (String parentName : parents) {
            ObjectNode parent = (ObjectNode) schemas.get(parentName);
            JsonNode discriminator = parent.get("discriminator");
            String propertyName = discriminator.get("propertyName").asString();
            ObjectNode inherited = parent.deepCopy();
            inherited.remove("discriminator");

            ArrayNode oneOf = schemas.arrayNode();
            for (Map.Entry<String, JsonNode> mapped : discriminator.get("mapping").properties()) {
                String ref = mapped.getValue().asString();
                String childName = ref.substring(ref.lastIndexOf('/') + 1);
                if (childName.equals(parentName)) {
                    // T1052 — a value mapped back at the parent, which
                    // PublicFieldConfig does for BOOLEAN, DOUBLE, LONG and DATE:
                    // those types add nothing to the base. It must be inlined rather
                    // than referenced, because the parent is about to *become* this
                    // oneOf and a $ref to it would be a branch containing itself —
                    // a StackOverflow, and the reason this branch exists.
                    oneOf.add(selfBranch(inherited, propertyName, mapped.getKey(), schemas));
                    continue;
                }
                JsonNode child = schemas.get(childName);
                if (child == null) {
                    continue;
                }
                absorbParent((ObjectNode) child, parentName, inherited, propertyName,
                        mapped.getKey(), schemas);
                oneOf.add(schemas.objectNode().put("$ref", ref));
            }
            ObjectNode replacement = schemas.objectNode();
            replacement.set("oneOf", oneOf);
            schemas.set(parentName, replacement);
        }
    }

    /**
     * The branch for a discriminator value the mapping points back at its own
     * parent: the parent's inherited constraints plus the {@code enum} pinning that
     * value, inlined so no {@code $ref} closes a cycle.
     */
    private static ObjectNode selfBranch(ObjectNode inherited, String propertyName,
            String discriminatorValue, ObjectNode schemas) {
        ObjectNode branch = schemas.objectNode();
        ArrayNode allOf = schemas.arrayNode();
        allOf.add(inherited);
        allOf.add(discriminatorPin(propertyName, discriminatorValue, schemas));
        branch.set("allOf", allOf);
        return branch;
    }

    private static ObjectNode discriminatorPin(String propertyName, String discriminatorValue,
            ObjectNode schemas) {
        ObjectNode pin = schemas.objectNode();
        ObjectNode properties = schemas.objectNode();
        properties.set(propertyName,
                schemas.objectNode().set("enum", schemas.arrayNode().add(discriminatorValue)));
        pin.set("properties", properties);
        return pin;
    }

    /**
     * Replaces a child's {@code allOf: [{$ref: parent}]} with the parent's own
     * constraints, plus an {@code enum} pinning its discriminator value.
     *
     * <p>The pin is what makes {@code oneOf} usable: without it every branch would
     * be a candidate for every instance, so an invalid {@code FilterGroup} would
     * still "match" some other filter's schema and pass.
     */
    private static void absorbParent(ObjectNode child, String parentName, ObjectNode inherited,
            String propertyName, String discriminatorValue, ObjectNode schemas) {
        JsonNode allOf = child.get("allOf");
        if (allOf == null || !allOf.isArray()) {
            return;
        }
        ArrayNode kept = schemas.arrayNode();
        boolean absorbed = false;
        for (JsonNode item : allOf) {
            if (item.has("$ref") && item.get("$ref").asString().endsWith("/" + parentName)) {
                absorbed = true;
            } else {
                kept.add(item);
            }
        }
        if (!absorbed) {
            return;
        }
        kept.add(inherited);
        kept.add(discriminatorPin(propertyName, discriminatorValue, schemas));
        child.set("allOf", kept);
    }

    /** The spec's own {@code info.version}, so a test can pin what it validated against. */
    public static String specVersion() {
        return SPEC.at("/info/version").asString();
    }

    /**
     * Validates {@code body} against {@code #/components/schemas/<schemaName>}.
     *
     * @return the violations, each as its instance location and message; empty when
     *         Adobe would accept the body
     */
    public static List<String> violations(String body, String schemaName) {
        JsonNode instance;
        try {
            instance = JSON.readTree(body);
        } catch (RuntimeException e) {
            return List.of("body is not JSON: " + e.getMessage());
        }
        return schemaFor(schemaName).validate(instance).stream()
                .map(error -> error.getInstanceLocation() + ": " + error.getMessage())
                .sorted()
                .toList();
    }

    /**
     * Throws when Adobe would reject the body.
     *
     * <p>Only the first {@value #REPORTED_VIOLATIONS} violations are listed, with a
     * count of the rest. A {@code oneOf} reports why <em>every</em> branch failed, so
     * one wrong filter yields over a hundred lines and the useful one is buried; the
     * violations are sorted by instance location, so the ones nearest the actual
     * mistake come first.
     */
    public static void assertValid(String body, String schemaName) {
        List<String> violations = violations(body, schemaName);
        if (violations.isEmpty()) {
            return;
        }
        List<String> shown = violations.stream().limit(REPORTED_VIOLATIONS).toList();
        String more = violations.size() > shown.size()
                ? "%n  … and %d more".formatted(violations.size() - shown.size())
                : "";
        throw new AssertionError(
                "Adobe's Content AI %s (%s) would reject this request body:%n  %s%s%n%nBody:%n%s"
                        .formatted(schemaName, specVersion(),
                                String.join(System.lineSeparator() + "  ", shown),
                                more.isEmpty() ? "" : more.formatted(),
                                pretty(body)));
    }

    /**
     * The schema at the given component name.
     *
     * <p>The root handed to the registry is the <em>whole</em> OpenAPI document with a
     * {@code $ref} added at the top, rather than the extracted subschema: every
     * {@code $ref: '#/components/schemas/…'} inside resolves against the document it
     * came from, and a lifted subschema would leave all of them dangling.
     */
    private static Schema schemaFor(String schemaName) {
        ObjectNode root = (ObjectNode) SPEC.deepCopy();
        root.put("$ref", "#/components/schemas/" + schemaName);
        return REGISTRY.getSchema(root);
    }

    private static String pretty(String body) {
        try {
            return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(JSON.readTree(body));
        } catch (RuntimeException e) {
            return body;
        }
    }

    /** Renders violations onto one line, for a test that inspects rather than fails. */
    public static String render(List<String> violations) {
        return violations.stream().collect(Collectors.joining("; "));
    }
}
