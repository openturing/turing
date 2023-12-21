package com.viglet.turing.connector.aem.indexer.persistence;

import com.sun.istack.NotNull;
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
    EntityManager entityManager;
    public TurAemIndexingDAO() {
        entityManager = getEntityManager();
    }

    private EntityManager getEntityManager() {
        EntityManagerFactory factory =
                Persistence.createEntityManagerFactory("aemHibernate");
            return factory.createEntityManager();
    }

    public boolean existsByAemIdAndDateAndGroup(String id, Date date,
                                                String group) {
        EntityManager entityManager = getEntityManager();
        try {
            CriteriaBuilder builder = entityManager.getCriteriaBuilder();
            CriteriaQuery<TurAemIndexing> criteria = builder.createQuery(TurAemIndexing.class);
            Root<TurAemIndexing> from = criteria.from(TurAemIndexing.class);
            criteria.select(from);
            criteria.where(
                    builder.and(
                            builder.and(
                                    builder.equal(from.get("aemId"), id),
                                    builder.equal(from.get("date"), date)
                            ),
                            builder.equal(from.get("indexGroup"), group)
                    )
            );
            TypedQuery<TurAemIndexing> typed = entityManager.createQuery(criteria);

            return typed.getSingleResult() != null;
        } catch (NoResultException nre) {
            return false;
        } finally {
            entityManager.close();
        }
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
                            builder.equal(from.get("indexGroup"), group),
                            builder.notEqual(from.get("deltaId"), deltaId)
                    ));
            TypedQuery<TurAemIndexing> typed = entityManager.createQuery(criteria);
            return Optional.ofNullable(typed.getResultList());
        } catch (NoResultException nre) {
            return Optional.empty();
        }
    }

    public void deleteContentsWereDeIndexed(String group,
                                          String deltaId) {
        try {
            entityManager.getTransaction().begin();
            CriteriaBuilder builder = entityManager.getCriteriaBuilder();
            CriteriaDelete<TurAemIndexing> criteria = builder.createCriteriaDelete(TurAemIndexing.class);
            Root<TurAemIndexing> from = criteria.from(TurAemIndexing.class);
            criteria.where(
                    builder.and(
                            builder.equal(from.get("indexGroup"), group),
                            builder.notEqual(from.get("deltaId"), deltaId)
                    ));
            entityManager.getTransaction().commit();
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            entityManager.getTransaction().rollback();
        }
    }

    public Optional<TurAemIndexing> findByAemIdAndGroup(@NotNull final String id, @NotNull final String group) {

        try {
            CriteriaBuilder builder = entityManager.getCriteriaBuilder();
            CriteriaQuery<TurAemIndexing> criteria = builder.createQuery(TurAemIndexing.class);
            Root<TurAemIndexing> from = criteria.from(TurAemIndexing.class);
            criteria.select(from);
            criteria.where(
                    builder.and(
                            builder.equal(from.get("aemId"), id),
                            builder.equal(from.get("indexGroup"), group)
                    ));
            TypedQuery<TurAemIndexing> typed = entityManager.createQuery(criteria);
            return Optional.ofNullable(typed.getSingleResult());
        } catch (NoResultException nre) {
            return Optional.empty();
        }
    }


    public void save(TurAemIndexing turAemIndexing) {
        EntityManager entityManager = getEntityManager();
        try {
            entityManager.getTransaction().begin();
            entityManager.persist(turAemIndexing);
            entityManager.getTransaction().commit();
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            entityManager.getTransaction().rollback();
        }
    }

    public void update(TurAemIndexing turAemIndexing) {
        EntityManager entityManager = getEntityManager();
        try {
            entityManager.getTransaction().begin();
            entityManager.merge(turAemIndexing);
            entityManager.getTransaction().commit();
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            entityManager.getTransaction().rollback();
        }
    }

    public void close() {
        entityManager.close();
    }
}
