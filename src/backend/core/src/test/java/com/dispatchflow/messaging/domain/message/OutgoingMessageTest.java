package com.dispatchflow.messaging.domain.message;

import static com.dispatchflow.messaging.domain.MessagingFixtures.STOP;
import static com.dispatchflow.messaging.domain.MessagingFixtures.TEN;
import static com.dispatchflow.messaging.domain.MessagingFixtures.at;
import static com.dispatchflow.messaging.domain.MessagingFixtures.messageId;
import static com.dispatchflow.messaging.domain.MessagingFixtures.text;
import static com.dispatchflow.messaging.domain.message.StopMessagesBuilder.aStopMessages;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.messaging.domain.Wording;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** An {@link OutgoingMessage}'s parts, and what a stop's messages print. */
class OutgoingMessageTest {

    private static final MessageState ACCEPTED = new MessageState.Accepted(at(1), new ProviderReference("provider-1"));

    @Test
    void GivenNoPhone_WhenAMessageIsCreated_ThenItIsRefusedNamingThePhone() {
        assertThatThrownBy(() -> new OutgoingMessage(
                        messageId(1),
                        MessageType.ARRIVING_SOON,
                        1,
                        null,
                        text(new Wording.ArrivingSoon()),
                        Optional.empty(),
                        false,
                        TEN,
                        ACCEPTED))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("phone");
    }

    @Test
    void GivenAStopWithMessages_WhenItAndAMessageArePrinted_ThenNeitherThePhoneNorAnyTextShows() {
        StopMessages messages = aStopMessages().withOutForDelivery(1, ACCEPTED).build();

        assertThat(messages.toString())
                .doesNotContain("345")
                .doesNotContain("Shefa")
                .contains(STOP.toString());
        assertThat(messages.messages().get(0).toString()).doesNotContain("345").doesNotContain("Shefa");
    }
}
