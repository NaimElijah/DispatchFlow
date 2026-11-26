package com.dispatchflow.access;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.access.application.api.ActorRole;
import com.dispatchflow.access.domain.Language;
import com.dispatchflow.access.domain.account.EmailAddress;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The shared list of seeded accounts holds values Access will accept when #181 stores an account per
 * entry (ADR-133, ADR-147): each entry's ID, roles, address and language, and no address twice, on
 * the reserved {@code .test} domain alone, so no real person's address is ever seeded.
 */
class SeededAccountsFileTest {

    private static final String PREFIX = "dispatchflow.seeded-accounts.";
    private static final Set<String> PARTS = Set.of("id", "roles", "address", "language");
    private static final Properties LIST = new Properties();

    @BeforeAll
    static void readTheList() throws IOException {
        try (InputStream file = SeededAccountsFileTest.class.getResourceAsStream("/seeded-accounts.properties")) {
            assertThat(file).as("seeded-accounts.properties").isNotNull();
            LIST.load(file);
        }
    }

    private static Set<String> entries() {
        return LIST.stringPropertyNames().stream()
                .map(name -> name.substring(PREFIX.length(), name.lastIndexOf('.')))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String part(String entry, String part) {
        return LIST.getProperty(PREFIX + entry + "." + part);
    }

    @Test
    void GivenTheList_WhenItsPropertiesAreRead_ThenEachIsAnEntrysIdRolesAddressOrLanguage() {
        assertThat(LIST.stringPropertyNames()).allSatisfy(name -> {
            assertThat(name).startsWith(PREFIX);
            assertThat(PARTS).contains(name.substring(name.lastIndexOf('.') + 1));
        });
        assertThat(entries()).contains("admin", "dispatcher", "driver");
        assertThat(entries())
                .allSatisfy(entry -> assertThat(PARTS)
                        .allSatisfy(part -> assertThat(part(entry, part))
                                .as("%s's %s", entry, part)
                                .isNotBlank()));
    }

    @Test
    void GivenEachEntry_WhenItsValuesAreParsed_ThenAccessAcceptsEveryOne() {
        assertThat(entries()).allSatisfy(entry -> {
            UUID.fromString(part(entry, "id"));
            Arrays.stream(part(entry, "roles").split(",")).map(String::strip).forEach(ActorRole::valueOf);
            assertThat(EmailAddress.parse(part(entry, "address")).value()).endsWith(".test");
            Language.valueOf(part(entry, "language"));
        });
    }

    @Test
    void GivenTheEntries_WhenTheirIdsAndAddressesAreCompared_ThenNoneRepeats() {
        assertThat(entries().stream().map(entry -> part(entry, "id")).toList()).doesNotHaveDuplicates();
        assertThat(entries().stream().map(entry -> part(entry, "address")).toList())
                .doesNotHaveDuplicates();
    }
}
