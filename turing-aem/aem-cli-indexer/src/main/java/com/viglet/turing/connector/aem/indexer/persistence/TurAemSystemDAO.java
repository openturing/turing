package com.viglet.turing.connector.aem.indexer.persistence;

import lombok.extern.slf4j.Slf4j;

import javax.persistence.*;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import java.util.Optional;

@Slf4j
public class TurAemSystemDAO {
    EntityManager entityManager;
    public TurAemSystemDAO() {
        entityManager = getEntityManager();
    }

    private EntityManager getEntityManager() {
        EntityManagerFactory factory =
                Persistence.createEntityManagerFactory("aemHibernate");
            return factory.createEntityManager();
    }

    public Optional<TurAemSystem> findByConfig(String config) {
        try {
            CriteriaBuilder builder = entityManager.getCriteriaBuilder();
            CriteriaQuery<TurAemSystem> criteria = builder.createQuery(TurAemSystem.class);
            Root<TurAemSystem> from = criteria.from(TurAemSystem.class);
            criteria.select(from);
            criteria.where(
                          builder.equal(from.get("config"), config)
            );
            TypedQuery<TurAemSystem> typed = entityManager.createQuery(criteria);
            return Optional.ofNullable(typed.getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }


    public void save(TurAemSystem turAemSystem) {
        try {
            entityManager.getTransaction().begin();
            entityManager.persist(turAemSystem);
            entityManager.getTransaction().commit();
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            entityManager.getTransaction().rollback();
        }
    }

    public void update(TurAemSystem turAemSystem) {
        try {
            entityManager.getTransaction().begin();
            entityManager.merge(turAemSystem);
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
