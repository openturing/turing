package com.viglet.turing.lucene;

import com.viglet.turing.se.result.TurSEResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.Document;
import org.apache.lucene.store.FSDirectory;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

import static com.viglet.turing.lucene.TurLuceneConstants.STORE_LUCENE;

@Slf4j
public class TurLuceneUtils {
    @NotNull
    public static Optional<FSDirectory> getLuceneDirectory() {
        try {
            return Optional.of(FSDirectory.open(Paths.get(STORE_LUCENE)));
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            return Optional.empty();
        }
    }

    public static List<TurSEResult> documentsToSEResults(List<Document> documents) {
        List<TurSEResult> turSEResults = new ArrayList<>();
         documents.forEach(document -> {
             Map<String, Object> fields = new HashMap<>();

             document.getFields().forEach(field -> {
                 if (fields.containsKey(field.name())) {
                   /*  if (fields.get(field.name()) instanceof List) {
                         ((List<String>) fields.get(field.name())).add(document.get(field.name()));
                     }
                     else {
                         fields.put(field.name(), List.of(document.get(field.name())));
                     } */
                     fields.put(field.name(), List.of(document.get(field.name())));
                 }
                 else {
                     fields.put(field.name(), field.stringValue());
                 }
             });
             turSEResults.add(TurSEResult.builder()
                     .fields(fields).build());
         });
         return turSEResults;
    }
}
