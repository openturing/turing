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

package com.viglet.turing.connector.aem.persistence;

import com.sun.istack.NotNull;
import com.viglet.turing.commons.exception.TurRuntimeException;
import lombok.extern.slf4j.Slf4j;

import javax.persistence.*;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaDelete;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Slf4j
public class TurAemIndexingDAO {
    public static final String ONCE = "once";
    public static final String DELTA_ID = "deltaId";
    public static final String INDEX_GROUP = "indexGroup";
    public static final String DATE = "date";
    public static final String AEM_ID = "aemId";
    public static final String MANAGER_FACTORY = "aemHibernate";
    EntityManager entityManager;

    public TurAemIndexingDAO() {
        entityManager = getEntityManager();
    }

    private EntityManager getEntityManager() {
        EntityManagerFactory factory =
                Persistence.createEntityManagerFactory(MANAGER_FACTORY);
        return factory.createEntityManager();
    }

    public Optional<List<TurAemIndexing>> findContentsShouldBeDeIndexed(String group,
                                                                        String deltaId) {
        try {
            CriteriaBuilder builder = entityManager.getCriteriaBuilder();
            CriteriaQuery<TurAemIndexing> criteria = builder.createQuery(TurAemIndexing.class);
            Root<TurAemIndexing> from = criteria.from(TurAemIndexing.class);
            criteria.select(from);
            criteria.where(
                    builder.and(
                            builder.equal(from.get(INDEX_GROUP), group),
                            builder.notEqual(from.get(DELTA_ID), deltaId),
                            builder.notEqual(from.get(ONCE), true)
                    ));
            TypedQuery<TurAemIndexing> typed = entityManager.createQuery(criteria);
            return Optional.ofNullable(typed.getResultList());
        } catch (NoResultException nre) {
            return Optional.empty();
        }
    }

    public void deleteContentsToReindex(String group) {
        try {
            CriteriaBuilder builder = entityManager.getCriteriaBuilder();
            CriteriaDelete<TurAemIndexing> criteria = builder.createCriteriaDelete(TurAemIndexing.class);
            Root<TurAemIndexing> from = criteria.from(TurAemIndexing.class);
            criteria.where(
                    builder.and(
                            builder.equal(from.get(INDEX_GROUP), group),
                            builder.notEqual(from.get(ONCE), true)
                    ));
            entityManager.getTransaction().begin();
            entityManager.createQuery(criteria).executeUpdate();
            entityManager.getTransaction().commit();
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            entityManager.getTransaction().rollback();
        }
    }

    public void deleteContentsToReindexOnce(String group) {
        try {
            entityManager.getTransaction().begin();
            CriteriaBuilder builder = entityManager.getCriteriaBuilder();
            CriteriaDelete<TurAemIndexing> criteria = builder.createCriteriaDelete(TurAemIndexing.class);
            Root<TurAemIndexing> from = criteria.from(TurAemIndexing.class);
            criteria.where(
                    builder.and(
                            builder.equal(from.get(INDEX_GROUP), group),
                            builder.equal(from.get(ONCE), true)
                    ));
            entityManager.getTransaction().begin();
            entityManager.createQuery(criteria).executeUpdate();
            entityManager.getTransaction().commit();
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            entityManager.getTransaction().rollback();
            throw new TurRuntimeException(ex);
        }
    }

    public void deleteContentsWereDeIndexed(String group,
                                            String deltaId) {
        try {
            CriteriaBuilder builder = entityManager.getCriteriaBuilder();
            CriteriaDelete<TurAemIndexing> criteria = builder.createCriteriaDelete(TurAemIndexing.class);
            Root<TurAemIndexing> from = criteria.from(TurAemIndexing.class);
            criteria.where(
                    builder.and(
                            builder.equal(from.get(INDEX_GROUP), group),
                            builder.notEqual(from.get(DELTA_ID), deltaId),
                            builder.notEqual(from.get(ONCE), true)
                    ));
            entityManager.getTransaction().begin();
            entityManager.createQuery(criteria).executeUpdate();
            entityManager.getTransaction().commit();
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            entityManager.getTransaction().rollback();
            throw new TurRuntimeException(ex);
        }
    }

    public Optional<List<TurAemIndexing>> findByAemIdAndGroup(@NotNull final String id, @NotNull final String group) {

        try {
            return Optional.ofNullable(typedQueryByAemIdAndGroup(id, group).getResultList());
        } catch (NoResultException nre) {
            return Optional.empty();
        }
    }

    private TypedQuery<TurAemIndexing> typedQueryByAemIdAndGroup(@NotNull String id, @NotNull String group) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<TurAemIndexing> criteria = builder.createQuery(TurAemIndexing.class);
        Root<TurAemIndexing> from = criteria.from(TurAemIndexing.class);
        criteria.select(from);
        criteria.where(
                builder.and(
                        builder.equal(from.get(AEM_ID), id),
                        builder.equal(from.get(INDEX_GROUP), group)
                ));
        return entityManager.createQuery(criteria);
    }

    public boolean existsByAemIdAndGroup(String id,
                                         String group) {
        try {
            return typedQueryByAemIdAndGroup(id, group).getSingleResult() != null;
        } catch (NoResultException nre) {
            return false;
        }
    }

    public void save(TurAemIndexing turAemIndexing) {
        try {
            entityManager.getTransaction().begin();
            entityManager.persist(turAemIndexing);
            entityManager.getTransaction().commit();
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            entityManager.getTransaction().rollback();
            throw new TurRuntimeException(ex);
        }
    }

    public void update(TurAemIndexing turAemIndexing) {
        try {
            entityManager.getTransaction().begin();
            entityManager.merge(turAemIndexing);
            entityManager.getTransaction().commit();
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            entityManager.getTransaction().rollback();
            throw new TurRuntimeException(ex);
        }
    }

    public void close() {
        entityManager.close();
    }

    public boolean existsByAemIdAndGroupAndDateNotEqual(String id, String group, Date date) {
        try {
            CriteriaBuilder builder = entityManager.getCriteriaBuilder();
            CriteriaQuery<TurAemIndexing> criteria = builder.createQuery(TurAemIndexing.class);
            Root<TurAemIndexing> from = criteria.from(TurAemIndexing.class);
            criteria.select(from);
            criteria.where(
                    builder.and(
                            builder.equal(from.get(AEM_ID), id),
                            builder.equal(from.get(INDEX_GROUP), group),
                            builder.notEqual(from.get(DATE), date)
                    )
            );
            TypedQuery<TurAemIndexing> typed = entityManager.createQuery(criteria);
            return typed.getSingleResult() != null;
        } catch (NoResultException nre) {
            return false;
        }
    }

    public void deleteByAemIdAndGroup(String id, String group) {
        try {
            CriteriaBuilder builder = entityManager.getCriteriaBuilder();
            CriteriaDelete<TurAemIndexing> criteria = builder.createCriteriaDelete(TurAemIndexing.class);
            Root<TurAemIndexing> from = criteria.from(TurAemIndexing.class);
            criteria.where(
                    builder.and(
                            builder.equal(from.get(AEM_ID), id),
                            builder.equal(from.get(INDEX_GROUP), group)
                    ));
            entityManager.getTransaction().begin();
            entityManager.createQuery(criteria).executeUpdate();
            entityManager.getTransaction().commit();
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            entityManager.getTransaction().rollback();
            throw new TurRuntimeException(ex);
        }
    }
}
