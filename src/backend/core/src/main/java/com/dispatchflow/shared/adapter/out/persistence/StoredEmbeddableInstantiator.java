package com.dispatchflow.shared.adapter.out.persistence;

import com.dispatchflow.shared.StoredStateInvalid;
import java.util.Arrays;
import java.util.Objects;
import org.hibernate.metamodel.spi.EmbeddableInstantiator;
import org.hibernate.metamodel.spi.ValueAccess;
import org.jspecify.annotations.Nullable;

/**
 * Rebuilds a multi-column value from its stored columns through {@link
 * StoredStateInvalid#whileRestoring} (ADR-126), so a stored value its type refuses is an incident, not a
 * refusal. All columns null is a null value; some of them null is a damaged row. The kernel's
 * instantiators extend it, and so does a module's own when its value's constructor checks a rule
 * (ADR-127); a value whose parts may be null on their own needs another instantiator.
 *
 * <p>Hibernate hands an embeddable's values in the alphabetical order of its attributes' names, so
 * each subclass reads them by that order, not the record's.
 *
 * @param <T> the value
 */
public abstract class StoredEmbeddableInstantiator<T> implements EmbeddableInstantiator {

    private final Class<T> type;

    /**
     * Creates the instantiator.
     *
     * @param type the value it rebuilds
     */
    protected StoredEmbeddableInstantiator(Class<T> type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    /**
     * Rebuilds the value from columns that are all present.
     *
     * @param values the stored values, in alphabetical order of the attributes' names
     * @return the value
     */
    protected abstract T rebuild(ValueAccess values);

    /**
     * Rebuilds the stored value.
     *
     * @param values the stored values, in alphabetical order of the attributes' names
     * @return the value, or null when every column is null
     * @throws StoredStateInvalid when only some columns are null, or the value refuses them
     */
    @Override
    public final @Nullable Object instantiate(ValueAccess values) {
        Object[] stored = values.getValues();
        if (Arrays.stream(stored).allMatch(Objects::isNull)) {
            return null;
        }
        if (Arrays.stream(stored).anyMatch(Objects::isNull)) {
            throw new StoredStateInvalid(type, "a stored value lacks some of its columns");
        }
        return StoredStateInvalid.whileRestoring(type, () -> rebuild(values));
    }

    @Override
    public final boolean isInstance(Object object) {
        return type.isInstance(object);
    }

    @Override
    public final boolean isSameClass(Object object) {
        return object.getClass() == type;
    }
}
