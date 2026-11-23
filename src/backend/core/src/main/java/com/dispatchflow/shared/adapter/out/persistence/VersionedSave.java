package com.dispatchflow.shared.adapter.out.persistence;

import com.dispatchflow.shared.AggregateRoot;
import com.dispatchflow.shared.DomainEvent;
import com.dispatchflow.shared.application.StaleVersion;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceUnitUtil;
import jakarta.persistence.PessimisticLockException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * The way every persistence adapter's {@code save} writes an aggregate, so that every save moves its
 * root's version by exactly 1 (ADR-126 decision 3, BR-76). An {@code add} uses it too where another
 * transaction may store the same aggregate first; the rest insert and flush themselves.
 *
 * <p>Hibernate moves the root's {@code @Version} when the root's own columns or its owned
 * collections change, but not when only a child entity's columns do. So after flushing, a save that
 * left the version where it was locks the root with {@code PESSIMISTIC_FORCE_INCREMENT}, which
 * Hibernate writes at once, inside {@code lock}; {@code OPTIMISTIC_FORCE_INCREMENT} would wait for
 * the commit. A root never saved is inserted at version 0 and forces nothing.
 *
 * <p>An aggregate is saved once per transaction, and nothing flushes it between a change and its
 * save. Hibernate ignores the forced increment of a root its transaction already wrote, and moves
 * the version at every flush of a change, so a second save, or a call that flushes first (an {@code
 * add}, a query, or a native lock or write, each of which flushes the whole persistence context),
 * would store a version its events don't carry, and the next change would raise an {@code EventId}
 * a listener has already processed. The save checks both and fails loudly instead.
 *
 * <p>A lock it can't get is a conflict too. Hibernate takes the root's and a child's row locks in
 * one order for a child-only change and in the other for a change to the children's list, so two
 * writers of one aggregate can deadlock, and a writer can time out behind another. Either way
 * another transaction is changing the aggregate, so the save answers {@code StaleVersion}. So does
 * a first save that breaks a unique constraint: another transaction stored the same aggregate, or
 * one holding the same unique value, first. A retry then runs the use case against what is stored,
 * and its own rules refuse it if they should. Any other constraint violation is a bug, and the save
 * lets it through.
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
     * @param root the aggregate to write: new, or loaded in this transaction and changed since, and
     *     not saved in it before
     * @param repositorySave the adapter's Spring Data {@code save}, such as {@code plans::save}
     * @param <T> the aggregate type
     * @return the saved aggregate, which this transaction manages
     * @throws StaleVersion when another transaction saved the aggregate after this one loaded it,
     *     or holds a lock this save waited for until it deadlocked or timed out, or stored first a
     *     never-saved aggregate's key or unique value; its kind is the entity's name and its ID the
     *     root's key, and the conflict is its cause
     * @throws AssertionError when the save left the version where it was, or stored a version its
     *     events don't carry: the aggregate was saved twice in this transaction, or flushed between
     *     two of its changes
     */
    public <T extends AggregateRoot> T save(T root, UnaryOperator<T> repositorySave) {
        PersistenceUnitUtil units = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();
        @Nullable Object loadedAt = units.getVersion(root);
        List<DomainEvent> raised = root.pendingEvents();
        T saved;
        try {
            saved = repositorySave.apply(root);
            entityManager.flush();
            if (loadedAt != null && loadedAt.equals(units.getVersion(saved))) {
                entityManager.lock(saved, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
            }
        } catch (OptimisticLockException | OptimisticLockingFailureException conflict) {
            throw new StaleVersion(entityName(root), key(units, root), conflict);
        } catch (PessimisticLockException | LockTimeoutException | PessimisticLockingFailureException blocked) {
            throw new StaleVersion(entityName(root), key(units, root), blocked);
        } catch (ConstraintViolationException | DataIntegrityViolationException violation) {
            if (loadedAt == null && breaksAUniqueConstraint(violation)) {
                throw new StaleVersion(entityName(root), key(units, root), violation);
            }
            throw violation;
        }
        requireOneStep(units.getVersion(saved), loadedAt, raised);
        return saved;
    }

    private static void requireOneStep(@Nullable Object stored, @Nullable Object loadedAt, List<DomainEvent> raised) {
        if (!(stored instanceof Long storedVersion) || storedVersion.equals(loadedAt)) {
            throw new AssertionError("a save must move the version: save an aggregate once per transaction,"
                    + " before any call that flushes");
        }
        for (DomainEvent event : raised) {
            if (event.eventId().version().value() != storedVersion) {
                throw new AssertionError("an event must carry the version its save stored: "
                        + "don't flush an aggregate between two of its changes");
            }
        }
    }

    private static boolean breaksAUniqueConstraint(RuntimeException violation) {
        for (Throwable cause = violation; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException constraint) {
                return constraint.getKind() == ConstraintKind.UNIQUE;
            }
        }
        return false;
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
