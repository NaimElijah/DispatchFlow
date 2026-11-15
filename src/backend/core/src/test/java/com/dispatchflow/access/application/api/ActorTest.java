package com.dispatchflow.access.application.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.SessionId;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActorTest {

    private static final AccountId ACCOUNT = AccountId.of(UUID.fromString("0190a5e2-7c3b-7def-8a12-0000000000a1"));
    private static final AccountId OTHER = AccountId.of(UUID.fromString("0190a5e2-7c3b-7def-8a12-0000000000a2"));
    private static final SessionId SESSION = SessionId.of(UUID.fromString("0190a5e2-7c3b-7def-8a12-0000000000b1"));
    private static final String OPERATION = "publishPlan";
    private static final String TRACE = "4bf92f3577b34da6a3ce929d0e0e4736";

    private static CallContext call(Optional<AccountId> user, Optional<SessionId> session) {
        return new CallContext(user, session, Optional.empty(), OPERATION, TRACE);
    }

    @Test
    void GivenTheAccountsOwnSessionInTheCall_WhenAnActorIsBuilt_ThenItIsKept() {
        CallContext own = call(Optional.of(ACCOUNT), Optional.of(SESSION));

        Actor actor = new Actor(ACCOUNT, Set.of(ActorRole.DISPATCHER), own);

        assertThat(actor.account()).isEqualTo(ACCOUNT);
        assertThat(actor.roles()).containsExactly(ActorRole.DISPATCHER);
        assertThat(actor.call()).isEqualTo(own);
    }

    @Test
    void GivenACallThatNamesAnotherUser_WhenAnActorIsBuilt_ThenItIsRefused() {
        CallContext another = call(Optional.of(OTHER), Optional.of(SESSION));

        assertThatThrownBy(() -> new Actor(ACCOUNT, Set.of(ActorRole.ADMIN), another))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void GivenACallWithNoUser_WhenAnActorIsBuilt_ThenItIsRefused() {
        CallContext noUser = call(Optional.empty(), Optional.of(SESSION));

        assertThatThrownBy(() -> new Actor(ACCOUNT, Set.of(ActorRole.ADMIN), noUser))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void GivenACallWithNoSession_WhenAnActorIsBuilt_ThenItIsRefused() {
        CallContext noSession = call(Optional.of(ACCOUNT), Optional.empty());

        assertThatThrownBy(() -> new Actor(ACCOUNT, Set.of(ActorRole.ADMIN), noSession))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void GivenNoRoles_WhenAnActorIsBuilt_ThenItHoldsAnEmptySetThatCannotChange() {
        Actor actor = new Actor(ACCOUNT, Set.of(), call(Optional.of(ACCOUNT), Optional.of(SESSION)));

        assertThat(actor.roles()).isEmpty();
        assertThatThrownBy(() -> actor.roles().add(ActorRole.ADMIN)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void GivenAMutableRoleSet_WhenItChangesAfterTheActorIsBuilt_ThenTheActorKeepsItsCopy() {
        Set<ActorRole> roles = EnumSet.of(ActorRole.DRIVER);
        Actor actor = new Actor(ACCOUNT, roles, call(Optional.of(ACCOUNT), Optional.of(SESSION)));

        roles.add(ActorRole.ADMIN);

        assertThat(actor.roles()).containsExactly(ActorRole.DRIVER);
    }
}
