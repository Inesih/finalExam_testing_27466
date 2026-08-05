package com.auca.library.dao;

import com.auca.library.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.util.List;
import java.util.UUID;

public class GenericDao<T> {
    private final Class<T> entityClass;

    public GenericDao(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    public void save(T entity) {
        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            session.persist(entity);
            transaction.commit();
        } catch (Exception e) {
            safeRollback(transaction);
            throw new RuntimeException("Failed to save " + entityClass.getSimpleName()
                    + ": " + rootCause(e).getMessage(), e);
        }
    }

    public void update(T entity) {
        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            session.merge(entity);
            transaction.commit();
        } catch (Exception e) {
            safeRollback(transaction);
            throw new RuntimeException("Failed to update " + entityClass.getSimpleName()
                    + ": " + rootCause(e).getMessage(), e);
        }
    }

    public T findById(UUID id) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.get(entityClass, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to find " + entityClass.getSimpleName()
                    + " by id: " + rootCause(e).getMessage(), e);
        }
    }

    public List<T> findAll() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("from " + entityClass.getName(), entityClass).list();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load all " + entityClass.getSimpleName()
                    + ": " + rootCause(e).getMessage(), e);
        }
    }

    private void safeRollback(Transaction transaction) {
        if (transaction != null && transaction.isActive()) {
            try {
                transaction.rollback();
            } catch (Exception rbEx) {
                System.err.println("Rollback failed: " + rbEx.getMessage());
            }
        }
    }

    private Throwable rootCause(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}