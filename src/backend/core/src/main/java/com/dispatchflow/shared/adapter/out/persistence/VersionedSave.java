package com.dispatchflow.shared.adapter.out.persistence;

import com.dispatchflow.shared.AggregateRoot;
import com.dispatchflow.shared.application.StaleVersion;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceUnitUtil;
import java.util.Objects;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * The one way a persistence adapter's {@code add} and {@code save} write an aggregate, so that every
 * save moves its root's version by exactly 1 (ADR-126 decision 3, BR-76).
 *
 * <p>Hibernate moves the root's {@code @Version} when the root's own columns or its owned
 * collections change, but not when only a child entity's columns do. So after flushing, a save that
 * left the version where it was locks the root with {@code PESSIMISTIC_FORCE_INCREMENT}, which
 * Hibernate writes at once, inside {@code lock}; {@code OPTIMISTIC_FORCE_INCREMENT} would wait for
 * the commit. A root never saved is inserted at version 0 and forces nothing.
 *
 * <p>It runs in the caller's transaction: without one, the flush throws. A conflict leaves that
 * transaction rollback-only, so a retry re-runs the whole use case in a new one.
 */
@Component
public final class VersionedSave {

    private final EntityManager entityManager;

    /**
     * Creates the helper.
     *
     * @param entityManager the transaction-bound entity manager Spring provides
     */
    public VersionedSave(EntityManager entityManager) {
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
    }

    /**
     * Saves an aggregate through its adapter's Spring Data {@code save}, which publishes its pending
     * events, and moves its root's version by exactly 1.
     *
     * @param root the aggregate, never saved or loaded in this transaction
     * @param repositorySave the adapter's Spring Data {@code save}, such as {@code plans::save}
     * @param <T> the aggregate type
     * @return the saved aggregate, which this transaction manages
     * @throws StaleVersion when another transaction saved the aggregate after this one loaded it;
     *     its kind is the entity's name and its ID the root's key, and the conflict is its cause
     */
    public <T extends AggregateRoot> T save(T root, UnaryOperator<T> repositorySave) {
        PersistenceUnitUtil units = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();
        @Nullable Object loadedAt = units.getVersion(root);
        try {
            T saved = repositorySave.apply(root);
            entityManager.flush();
            if (loadedAt != null && loadedAt.equals(units.getVersion(saved))) {
                entityManager.lock(saved, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
            }
            return saved;
        } catch (OptimisticLockException | OptimisticLockingFailureException conflict) {
            throw new StaleVersion(entityName(root), key(units, root), conflict);
        }
    }

    private String entityName(AggregateRoot root) {
        return entityManager.getMetamodel().entity(root.getClass()).getName();
    }

    private static UUID key(PersistenceUnitUtil units, AggregateRoot root) {
        if (units.getIdentifier(root) instanceof UUID key) {
            return key;
        }
        throw new AssertionError("an aggregate root's key is a UUID (ADR-126)");
    }
}
