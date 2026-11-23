package com.dispatchflow.shared.adapter.out.persistence;

import com.dispatchflow.shared.StoredStateInvalid;
import jakarta.persistence.AttributeConverter;
import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Stores a single-value kernel type in one column, and rebuilds it through {@link
 * StoredStateInvalid#whileRestoring}, so a stored value its type now refuses is an incident rather
 * than a user's refusal (ADR-126). A {@code null} column is a {@code null} attribute: whether one
 * may be null is the mapped field's business.
 *
 * <p>Each concrete subclass is a {@code @Converter(autoApply = true)}, so no entity names it.
 *
 * @param <T> the kernel type
 * @param <C> the column's Java type
 */
public abstract class StoredValueConverter<T, C> implements AttributeConverter<T, C> {

    private final Class<T> type;
    private final Function<T, C> toColumn;
    private final Function<C, T> rebuild;

    /**
     * Creates the converter.
     *
     * @param type the kernel type, named in a {@link StoredStateInvalid}
     * @param toColumn reads the value the column stores
     * @param rebuild rebuilds the kernel type from a stored value
     */
    protected StoredValueConverter(Class<T> type, Function<T, C> toColumn, Function<C, T> rebuild) {
        this.type = Objects.requireNonNull(type, "type");
        this.toColumn = Objects.requireNonNull(toColumn, "toColumn");
        this.rebuild = Objects.requireNonNull(rebuild, "rebuild");
    }

    /**
     * The value to store.
     *
     * @param value the attribute, or null
     * @return its column value, or null for a null attribute
     */
    @Override
    public final @Nullable C convertToDatabaseColumn(@Nullable T value) {
        return value == null ? null : toColumn.apply(value);
    }

    /**
     * The attribute a stored value rebuilds.
     *
     * @param stored the column value, or null
     * @return the kernel value, or null for a null column
     * @throws StoredStateInvalid when the kernel type refuses the stored value
     */
    @Override
    public final @Nullable T convertToEntityAttribute(@Nullable C stored) {
        return stored == null ? null : StoredStateInvalid.whileRestoring(type, () -> rebuild.apply(stored));
    }
}
