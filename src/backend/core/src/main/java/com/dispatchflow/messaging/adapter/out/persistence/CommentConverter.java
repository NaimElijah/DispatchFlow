package com.dispatchflow.messaging.adapter.out.persistence;

import com.dispatchflow.messaging.domain.rating.Comment;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;

/** Stores a recipient's {@link Comment} as its text, rebuilt through {@code Comment.ofStored}. */
@Converter(autoApply = true)
public final class CommentConverter extends StoredValueConverter<Comment, String> {

    /** Creates the converter, which Hibernate does once. */
    public CommentConverter() {
        super(Comment.class, Comment::text, Comment::ofStored);
    }
}
