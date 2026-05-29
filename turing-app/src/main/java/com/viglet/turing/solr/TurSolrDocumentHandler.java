package com.viglet.turing.solr;

import static com.viglet.turing.solr.TurSolrConstants.BOOST;
import static com.viglet.turing.solr.TurSolrConstants.SCORE;
import static com.viglet.turing.solr.TurSolrConstants.TURING_ENTITY;
import static com.viglet.turing.solr.TurSolrConstants.TYPE;
import static com.viglet.turing.solr.TurSolrConstants.VERSION;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.common.SolrInputDocument;
import org.json.JSONArray;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteField;
import com.viglet.turing.sn.field.TurSNSiteFieldService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TurSolrDocumentHandler {
    private final int commitWithin;
    private final TurSNSiteFieldService turSNSiteFieldUtils;
    private final TurDecimalFieldNormalizer turDecimalFieldNormalizer;

    public TurSolrDocumentHandler(int commitWithin, TurSNSiteFieldService turSNSiteFieldUtils,
            TurDecimalFieldNormalizer turDecimalFieldNormalizer) {
        this.commitWithin = commitWithin;
        this.turSNSiteFieldUtils = turSNSiteFieldUtils;
        this.turDecimalFieldNormalizer = turDecimalFieldNormalizer;
    }

    public void indexing(TurSolrInstance turSolrInstance, TurSNSite turSNSite,
            Map<String, Object> attributes) {
        log.debug("Executing indexing ...");
        attributes.remove(SCORE);
        attributes.remove(VERSION);
        attributes.remove(BOOST);
        this.addDocument(turSolrInstance, turSNSite, attributes);
    }

    /**
     * Indexes a batch of documents in a single Solr update request — drops the
     * per-doc HTTP overhead during bulk imports.
     *
     * <p>Throws on failure so the surrounding Resilience4j retry/circuit-breaker
     * sees the error and can retry the batch. Callers that need graceful
     * degradation (e.g. fall back to per-doc indexing) catch
     * {@link RuntimeException}.
     *
     * @return number of documents sent to Solr.
     */
    public int indexingBatch(TurSolrInstance turSolrInstance, TurSNSite turSNSite,
            List<Map<String, Object>> documents) {
        if (documents == null || documents.isEmpty()) {
            return 0;
        }
        log.debug("Executing batch indexing for {} documents ...", documents.size());
        Map<String, TurSNSiteField> turSNSiteFieldMap = turSNSiteFieldUtils.toMap(turSNSite);
        List<SolrInputDocument> solrDocs = new ArrayList<>(documents.size());
        for (Map<String, Object> attributes : documents) {
            if (attributes == null) {
                continue;
            }
            attributes.remove(SCORE);
            attributes.remove(VERSION);
            attributes.remove(BOOST);
            SolrInputDocument document = new SolrInputDocument();
            attributes.forEach((key, value) -> processAttribute(turSNSiteFieldMap, document, key, value));
            solrDocs.add(document);
        }
        if (solrDocs.isEmpty()) {
            return 0;
        }
        try {
            UpdateRequest updateRequest = new UpdateRequest();
            updateRequest.add(solrDocs);
            updateRequest.setCommitWithin(commitWithin);
            updateRequest.process(turSolrInstance.getSolrClient(), turSolrInstance.getCore());
            return solrDocs.size();
        } catch (SolrServerException | IOException e) {
            // Throw so the resilience layer (and the import service's per-doc
            // fallback) can react; the caller logs the final outcome.
            throw new TurSolrIndexException(
                    "Batch indexing of " + solrDocs.size() + " documents failed: " + e.getMessage(), e);
        }
    }

    /**
     * Marker exception for failed Solr write operations (single-doc, batch, and
     * delete). Carries the original Solr/IO failure as its cause; matched by the
     * Resilience4j retry config and by the import service's per-doc fallback.
     */
    public static class TurSolrIndexException extends RuntimeException {
        public TurSolrIndexException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public void deIndexing(TurSolrInstance turSolrInstance, String id) {
        log.debug("Executing deIndexing ...");
        this.deleteDocument(turSolrInstance, id);
    }

    public void deIndexingByType(TurSolrInstance turSolrInstance, String type) {
        log.debug("Executing deIndexing by type {}...", type);
        this.deleteDocumentByType(turSolrInstance, type);
    }

    public void deleteDocument(TurSolrInstance turSolrInstance, String id) {
        try {
            UpdateRequest updateRequest = new UpdateRequest();
            updateRequest.deleteById(id);
            updateRequest.setCommitWithin(commitWithin);
            updateRequest.process(turSolrInstance.getSolrClient(), turSolrInstance.getCore());
        } catch (SolrServerException | IOException e) {
            // Surface the failure instead of swallowing it — a logged-and-ignored error
            // here silently reports a de-index as successful while the document remains.
            throw new TurSolrIndexException("Error de-indexing Solr document id=" + id, e);
        }
    }

    public void deleteDocumentByType(TurSolrInstance turSolrInstance, String type) {
        try {
            UpdateRequest updateRequest = new UpdateRequest();
            updateRequest.deleteByQuery(TYPE + ":" + type);
            updateRequest.setCommitWithin(commitWithin);
            updateRequest.process(turSolrInstance.getSolrClient(), turSolrInstance.getCore());
        } catch (SolrServerException | IOException e) {
            throw new TurSolrIndexException("Error de-indexing Solr documents type=" + type, e);
        }
    }

    private String concatenateString(@SuppressWarnings("rawtypes") List list) {
        int i = 0;
        StringBuilder sb = new StringBuilder();
        for (Object valueItem : list) {
            sb.append(TurSolrField.convertFieldToString(valueItem));
            if (i++ != list.size() - 1)
                sb.append(System.lineSeparator());
        }
        return sb.toString().trim();
    }

    public void addDocument(TurSolrInstance turSolrInstance, TurSNSite turSNSite,
            Map<String, Object> attributes) {
        Map<String, TurSNSiteField> turSNSiteFieldMap = turSNSiteFieldUtils.toMap(turSNSite);
        SolrInputDocument document = new SolrInputDocument();
        Optional.ofNullable(attributes).ifPresent(attr -> {
            attr.forEach((key, value) -> processAttribute(turSNSiteFieldMap, document, key, value));
            addSolrDocument(turSolrInstance, document);
        });

    }

    private void processAttribute(Map<String, TurSNSiteField> turSNSiteFieldMap,
            SolrInputDocument document, String key, Object attribute) {
        Optional.ofNullable(attribute).ifPresent(attr -> {
            TurSNSiteField turSNSiteField = turSNSiteFieldMap.get(key);
            TurSEFieldType fieldType = Optional.ofNullable(turSNSiteField)
                    .map(TurSNSiteField::getType)
                    .orElse(null);

            if (turDecimalFieldNormalizer.isDecimalFieldType(fieldType)) {
                processDecimalAttribute(turSNSiteFieldMap, document, key, attr, fieldType);
                return;
            }

            switch (attr) {
                case Integer integer -> processInteger(document, key, integer);
                case JSONArray objects -> processJSONArray(turSNSiteFieldMap, document, key,
                        objects);
                case ArrayList<?> arrayList -> processArrayList(turSNSiteFieldMap, document, key,
                        arrayList);
                default -> processOtherTypes(document, key, attribute);
            }
        });
    }

    private void processDecimalAttribute(Map<String, TurSNSiteField> turSNSiteFieldMap,
            SolrInputDocument document, String key, Object attribute, TurSEFieldType fieldType) {
        boolean multiValued = isMultiValued(turSNSiteFieldMap, key);

        switch (attribute) {
            case JSONArray jsonArray -> {
                if (jsonArray.length() == 0) {
                    return;
                }

                if (multiValued) {
                    IntStream.range(0, jsonArray.length())
                            .mapToObj(jsonArray::get)
                            .forEach(value -> addDecimalValue(document, key, value, fieldType));
                } else {
                    addDecimalValue(document, key, jsonArray.get(0), fieldType);
                }
            }
            case ArrayList<?> arrayList -> {
                if (arrayList.isEmpty()) {
                    return;
                }

                if (multiValued) {
                    arrayList.forEach(value -> addDecimalValue(document, key, value, fieldType));
                } else {
                    addDecimalValue(document, key, arrayList.getFirst(), fieldType);
                }
            }
            default -> addDecimalValue(document, key, attribute, fieldType);
        }
    }

    private void addDecimalValue(SolrInputDocument document, String key, Object value,
            TurSEFieldType fieldType) {
        if (fieldType == TurSEFieldType.CURRENCY) {
            String rawCurrency = TurSolrField.convertFieldToString(value);
            toSolrCurrencyValue(rawCurrency)
                    .ifPresentOrElse(
                            normalizedCurrency -> document.addField(key, normalizedCurrency),
                            () -> log.warn("Skipping invalid currency value for field '{}': {}", key,
                                    rawCurrency));
            return;
        }

        turDecimalFieldNormalizer.normalizeNumericValue(fieldType, value)
                .ifPresentOrElse(
                        normalizedValue -> document.addField(key, normalizedValue),
                        () -> log.warn("Skipping invalid decimal value for field '{}': {}", key,
                                TurSolrField.convertFieldToString(value)));
    }

    private Optional<String> toSolrCurrencyValue(String rawCurrency) {
        if (rawCurrency == null) {
            return Optional.empty();
        }

        String normalizedCurrency = rawCurrency.trim();
        int separatorIndex = normalizedCurrency.lastIndexOf(',');
        if (separatorIndex <= 0 || separatorIndex == normalizedCurrency.length() - 1) {
            return Optional.empty();
        }

        String amountPart = normalizedCurrency.substring(0, separatorIndex).trim();
        String currencyCode = normalizedCurrency.substring(separatorIndex + 1).trim()
                .toUpperCase(Locale.ROOT);
        if (!currencyCode.matches("^[A-Z]{3}$")) {
            return Optional.empty();
        }

        // Currency amounts in API payloads use dot as decimal separator (standard
        // JSON/REST format).
        // Also accept comma-decimal notation (e.g. "19,92,BRL") by normalizing to dot
        // form.
        // Preserve the original string to avoid reformatting (e.g. "150.00" must not
        // become "150.0").
        String canonicalAmount = amountPart.replace(',', '.');
        try {
            Double.parseDouble(canonicalAmount); // validate only
            return Optional.of(String.format(Locale.ROOT, "%s,%s", canonicalAmount, currencyCode));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private void processOtherTypes(SolrInputDocument document, String key, Object attribute) {
        document.addField(key, TurSolrField.convertFieldToString(attribute));
    }

    private void processInteger(SolrInputDocument document, String key, Object attribute) {
        document.addField(key, attribute);
    }

    private void processJSONArray(Map<String, TurSNSiteField> turSNSiteFieldMap,
            SolrInputDocument document, String key, Object attribute) {
        JSONArray value = (JSONArray) attribute;
        if (key.startsWith(TURING_ENTITY) || isMultiValued(turSNSiteFieldMap, key)) {
            Optional.ofNullable(value).ifPresent(v -> IntStream.range(0, value.length())
                    .forEachOrdered(i -> document.addField(key, value.getString(i))));
        } else {
            document.addField(key,
                    concatenateString(Optional.ofNullable(value)
                            .map(v -> IntStream.range(0, value.length()).mapToObj(value::getString)
                                    .collect(Collectors.toCollection(ArrayList::new)))
                            .orElse(new ArrayList<>())));
        }
    }

    private static boolean isMultiValued(Map<String, TurSNSiteField> turSNSiteFieldMap,
            String key) {
        return turSNSiteFieldMap.get(key) != null
                && turSNSiteFieldMap.get(key).getMultiValued() == 1;
    }

    private void processArrayList(Map<String, TurSNSiteField> turSNSiteFieldMap,
            SolrInputDocument document, String key, Object attribute) {
        @SuppressWarnings("rawtypes")
        List attributeList = (ArrayList) attribute;
        Optional.ofNullable(attributeList).ifPresent(values -> {
            if (key.startsWith(TURING_ENTITY) || isMultiValued(turSNSiteFieldMap, key)) {
                for (Object valueItem : values) {
                    document.addField(key, TurSolrField.convertFieldToString(valueItem));
                }
            } else {
                document.addField(key, concatenateString(values));
            }
        });
    }

    private void addSolrDocument(TurSolrInstance turSolrInstance, SolrInputDocument document) {
        try {
            UpdateRequest updateRequest = new UpdateRequest();
            updateRequest.add(document);
            updateRequest.setCommitWithin(commitWithin);
            updateRequest.process(turSolrInstance.getSolrClient(), turSolrInstance.getCore());
        } catch (SolrServerException | IOException e) {
            // Propagate instead of swallowing: a logged-and-ignored error here would
            // falsely report the document as "Indexed" while it was actually lost. Letting
            // it surface lets the resilience layer retry transient failures and the circuit
            // breaker react to persistent ones.
            throw new TurSolrIndexException("Error writing Solr document: " + e.getMessage(), e);
        }
    }
}
