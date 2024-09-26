/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.connector.aem.persistence.repository;

import com.viglet.turing.connector.aem.persistence.model.TurAemIndexing;
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
