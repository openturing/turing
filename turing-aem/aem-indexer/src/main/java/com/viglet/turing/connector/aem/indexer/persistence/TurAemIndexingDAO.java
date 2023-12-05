package com.viglet.turing.connector.aem.indexer.persistence;

import com.sun.istack.NotNull;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import javax.persistence.*;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

@Slf4j
public class TurAemIndexingDAO {
    private static TurAemIndexingDAO instance;
    protected EntityManager entityManager;

    public static TurAemIndexingDAO getInstance() {
        if (instance == null) {
            instance = new TurAemIndexingDAO();
        }

        return instance;
    }

    private TurAemIndexingDAO() {
        entityManager = getEntityManager();
    }

    private EntityManager getEntityManager() {
        EntityManagerFactory factory =
                Persistence.createEntityManagerFactory("aemHibernate");
        if (entityManager == null) {
            entityManager = factory.createEntityManager();
        }

        return entityManager;
    }

    public TurAemIndexing getById(final int id) {
        return entityManager.find(TurAemIndexing.class, id);
    }

    @SuppressWarnings("unchecked")
    public List<TurAemIndexing> findAll() {
        return entityManager.createQuery("FROM " +
                TurAemIndexing.class.getName()).getResultList();
    }

    public boolean existsByAemIdAndDateAndGroup(@NotNull final String id, @NotNull final Date date,
                                                @NotNull final String group) {
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
        }
    }

    public Optional<List<TurAemIndexing>> findContentsShouldBeDeIndexed(@NotNull final String group,
                                                                        @NotNull final String deltaId) {
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

    public void deleteContentsWereIndexed(@NotNull final String group,
                                          @NotNull final String deltaId) {
        List<Integer> ids = new ArrayList<>();
        findContentsShouldBeDeIndexed(group, deltaId).ifPresent(contents ->
                contents.forEach(turAemIndexing -> ids.add(turAemIndexing.getId())
                ));
        try {
            entityManager.getTransaction().begin();
            Query query = entityManager.createQuery("DELETE TurAemIndexing tur WHERE id IN (:ids)");
            query.setParameter("ids", ids);
            query.executeUpdate();
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


    public void persist(TurAemIndexing turAemIndexing) {
        try {
            entityManager.getTransaction().begin();
            entityManager.persist(turAemIndexing);
            entityManager.getTransaction().commit();
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            entityManager.getTransaction().rollback();
        }
    }

    public void merge(TurAemIndexing turAemIndexing) {
        try {
            entityManager.getTransaction().begin();
            entityManager.merge(turAemIndexing);
            entityManager.getTransaction().commit();
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            entityManager.getTransaction().rollback();
        }
    }

    public void remove(TurAemIndexing turAemIndexing) {
        try {
            entityManager.getTransaction().begin();
            turAemIndexing = entityManager.find(TurAemIndexing.class, turAemIndexing.getId());
            entityManager.remove(turAemIndexing);
            entityManager.getTransaction().commit();
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            entityManager.getTransaction().rollback();
        }
    }

    public void removeById(final int id) {
        try {
            TurAemIndexing turAemIndexing = getById(id);
            remove(turAemIndexing);
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
        }
    }
}
