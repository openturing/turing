package com.viglet.turing.connector.aem.indexer.persistence.repository;

import com.viglet.turing.connector.aem.indexer.persistence.model.TurAemIndexing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface TurAemIndexingRepository extends JpaRepository<TurAemIndexing, String> {

    Optional<List<TurAemIndexing>> findByIndexGroupAndDeltaIdNotAndOnceFalse(String indexGroup, String deltaId);

    default Optional<List<TurAemIndexing>> findContentsShouldBeDeIndexed(String indexGroup, String deltaId) {
        return findByIndexGroupAndDeltaIdNotAndOnceFalse(indexGroup, deltaId);
    }

    boolean existsByAemIdAndDateAndIndexGroup(String aemId, Date date, String indexGroup);
    boolean existsByAemIdAndIndexGroup(String aemId, String indexGroup);
    boolean existsByAemIdAndIndexGroupAndDateNot(String aemId, String indexGroup, Date date);
    Optional<List<TurAemIndexing>> findByAemIdAndIndexGroup(String aemId, String indexGroup);

    void deleteByIndexGroupAndOnceFalse(String indexGroup);

    default void deleteContentsToReindex(String indexGroup) {
        deleteByIndexGroupAndOnceFalse(indexGroup);
    }

    void deleteByIndexGroupAndOnceTrue(String indexGroup);

    default void deleteContentsToReindexOnce(String indexGroup) {
        deleteByIndexGroupAndOnceTrue(indexGroup);
    }

    void deleteByIndexGroupAndDeltaIdNotAndOnceFalse(String indexGroup,
                                                    String deltaId);
    @Transactional
    default void deleteContentsWereDeIndexed(String indexGroup,
                                             String deltaId) {
        deleteByIndexGroupAndDeltaIdNotAndOnceFalse(indexGroup, deltaId);
    }
    void deleteByAemIdAndIndexGroup(String aemId, String indexGroup);
}
