package com.dispatchflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.DatabaseSessionGuard.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Each session the core refuses to start on, and the one it accepts, decided from what the guard
 * reads of the session. {@code DatabaseSessionGuardIT} proves the reading against a real
 * PostgreSQL, a server that translates its messages included.
 */
class DatabaseSessionGuardTest {

    private static final String UNTRANSLATED = "C";

    /** ADR-145's app: no attribute, no membership, nothing owned, untranslated messages. */
    private static Session app() {
        return new Session("app", "app", false, false, false, false, false, null, false, UNTRANSLATED);
    }

    @Test
    void GivenAppsSession_WhenTheGuardDecides_ThenNothingIsRefused() {
        assertThat(app().refusal()).isEmpty();
    }

    /** A superuser carries every attribute; each is named, so the fix is plain from the message. */
    @Test
    void GivenASuperusersSession_WhenTheGuardDecides_ThenItRefusesNamingEveryAttribute() {
        Session superuser =
                new Session("postgres", "postgres", true, true, true, true, true, null, false, UNTRANSLATED);

        assertThat(superuser.refusal())
                .hasValueSatisfying(message -> assertThat(message)
                        .contains("postgres")
                        .contains("SUPERUSER, CREATEDB, CREATEROLE, REPLICATION, BYPASSRLS"));
    }

    /** A login that switches to app with SET ROLE can switch back with RESET ROLE. */
    @Test
    void GivenALoginThatSwitchedToApp_WhenTheGuardDecides_ThenItRefusesNamingBothRoles() {
        Session switched =
                new Session("dispatchflow_owner", "app", false, false, false, false, false, null, false, UNTRANSLATED);

        assertThat(switched.refusal())
                .hasValueSatisfying(
                        message -> assertThat(message).contains("logs in as dispatchflow_owner and switches to app"));
    }

    /** Without the login, a switched session can't be told apart, so it isn't trusted. */
    @Test
    void GivenASessionWhoseLoginTheServerDoesNotShow_WhenTheGuardDecides_ThenItRefuses() {
        Session unknown = new Session(null, "app", false, false, false, false, false, null, false, UNTRANSLATED);

        assertThat(unknown.refusal())
                .hasValueSatisfying(message -> assertThat(message).contains("shows no login"));
    }

    @Test
    void GivenARoleThatIsAMemberOfAnother_WhenTheGuardDecides_ThenItRefusesNamingTheRoles() {
        Session member =
                new Session("app", "app", false, false, false, false, false, "dispatchflow_owner", false, UNTRANSLATED);

        assertThat(member.refusal())
                .hasValueSatisfying(message -> assertThat(message).contains("member of dispatchflow_owner"));
    }

    @Test
    void GivenTheOwnersSession_WhenTheGuardDecides_ThenItRefusesNamingTheRole() {
        Session owner = new Session(
                "dispatchflow_owner",
                "dispatchflow_owner",
                false,
                false,
                false,
                false,
                false,
                null,
                true,
                UNTRANSLATED);

        assertThat(owner.refusal())
                .hasValueSatisfying(message -> assertThat(message).contains("dispatchflow_owner owns an object"));
    }

    /** Hibernate would no longer find a violated constraint's name, so four races answer 500 (#250). */
    @Test
    void GivenAServerWhoseMessagesAreHebrew_WhenTheGuardDecides_ThenItRefusesNamingTheSetting() {
        Session hebrew = new Session("app", "app", false, false, false, false, false, null, false, "he_IL.UTF-8");

        assertThat(hebrew.refusal())
                .hasValueSatisfying(message -> assertThat(message).contains("lc_messages is 'he_IL.UTF-8'"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"C", "c", "C.UTF-8", "C.utf8", "POSIX"})
    void GivenAnUntranslatedMessagesSetting_WhenTheGuardReadsIt_ThenItAccepts(String lcMessages) {
        assertThat(DatabaseSessionGuard.leavesMessagesUntranslated(lcMessages)).isTrue();
    }

    /**
     * An English locale is refused too: a LANGUAGE variable in the server's environment translates
     * its messages (ADR-152). An empty setting inherits that environment, which the core can't see.
     * Czech and Catalan start with the C locale's letter.
     */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "en_US.UTF-8",
                "en_US.utf8",
                "en_GB",
                "en",
                "en.UTF-8",
                "he_IL.UTF-8",
                "de_DE.utf8",
                "cs_CZ.UTF-8",
                "ca_ES",
                "Chinese",
                "POSIX.UTF-8",
                ""
            })
    void GivenAnotherMessagesSetting_WhenTheGuardReadsIt_ThenItRefuses(String lcMessages) {
        assertThat(DatabaseSessionGuard.leavesMessagesUntranslated(lcMessages)).isFalse();
    }
}
