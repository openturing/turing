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

package com.viglet.turing.connector.persistence.repository;

import com.viglet.turing.connector.persistence.model.TurConnectorIndexing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface TurConnectorIndexingRepository extends JpaRepository<TurConnectorIndexing, String> {

    Optional<List<TurConnectorIndexing>> findByIndexGroupAndTransactionIdNot(String indexGroup, String transactionId);
    default Optional<List<TurConnectorIndexing>> findContentsShouldBeDeIndexed(String indexGroup, String transactionId) {
        return findByIndexGroupAndTransactionIdNot(indexGroup, transactionId);
    }
    boolean existsByObjectIdAndIndexGroup(String objectId, String indexGroup);
    boolean existsByObjectIdAndIndexGroupAndChecksumNot(String objectId, String indexGroup, String checksum);
    Optional<List<TurConnectorIndexing>> findByObjectIdAndIndexGroup(String objectId, String indexGroup);
    void deleteByObjectIdAndIndexGroup(String objectId, String indexGroup);

    void deleteByIndexGroupAndTransactionIdNot(String indexGroup,
                                                     String transactionId);
    @Transactional
    default void deleteContentsWereDeIndexed(String indexGroup,
                                             String deltaId) {
        deleteByIndexGroupAndTransactionIdNot(indexGroup, deltaId);
    }
}
