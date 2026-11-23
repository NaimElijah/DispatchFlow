package com.dispatchflow.messaging.adapter.out.persistence;

import com.dispatchflow.messaging.domain.MessageText;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;

/** Stores a message's {@link MessageText} as its text, rebuilt through {@code MessageText.ofStored}. */
@Converter(autoApply = true)
public final class MessageTextConverter extends StoredValueConverter<MessageText, String> {

    /** Creates the converter, which Hibernate does once. */
    public MessageTextConverter() {
        super(MessageText.class, MessageText::text, MessageText::ofStored);
    }
}
