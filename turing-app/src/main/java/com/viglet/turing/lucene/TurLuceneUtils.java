package com.viglet.turing.lucene;

import com.viglet.turing.se.result.TurSEResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.Document;
import org.apache.lucene.store.FSDirectory;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static com.viglet.turing.lucene.TurLuceneConstants.STORE_LUCENE;

@Slf4j
public class TurLuceneUtils {

    private TurLuceneUtils() {
        throw new IllegalStateException("Lucene Utils class");
    }

    @NotNull
    public static Optional<FSDirectory> getLuceneDirectory() {
        try {
            return Optional.of(FSDirectory.open(Paths.get(STORE_LUCENE)));
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            return Optional.empty();
        }
    }
}
