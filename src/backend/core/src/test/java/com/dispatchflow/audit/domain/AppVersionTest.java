package com.dispatchflow.audit.domain;

import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.audit.domain.AuditError.InvalidAppVersion;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * ADR-110: the app version a client declares is a semantic version's three numbers (NFR-93,
 * ADR-082), so no text the client sends reaches an audit record.
 */
class AppVersionTest {

    @ParameterizedTest
    @CsvSource({
        "1.4.2, 1, 4, 2",
        "0.0.0, 0, 0, 0",
        "10.20.30, 10, 20, 30",
        "2147483647.0.1, 2147483647, 0, 1",
        "0.2147483647.0, 0, 2147483647, 0",
        "0.0.2147483647, 0, 0, 2147483647"
    })
    void GivenThreeDotSeparatedNumbers_WhenParsed_ThenEachPartIsKept(String text, int major, int minor, int patch) {
        assertThat(AppVersion.parse(text)).isEqualTo(new AppVersion(major, minor, patch));
    }

    /** Text that is not three dot-separated numbers without leading zeros, each within an int. */
    static Stream<String> notThreeNumbersWithinRange() {
        return Stream.of(
                "",
                " ",
                "1",
                "1.4",
                "1.4.2.3",
                "1..2",
                ".1.2",
                "1.2.",
                "01.4.2",
                "1.04.2",
                "1.4.02",
                "v1.4.2",
                "1.4.2-beta.1",
                "1.4.2+5",
                " 1.4.2",
                "1.4.2 ",
                "-1.0.0",
                "+1.0.0",
                "2147483648.0.0",
                "0.2147483648.0",
                "0.0.2147483648",
                "99999999999.0.0",
                "١.٢.٣",
                "1.4.x");
    }

    @ParameterizedTest
    @MethodSource("notThreeNumbersWithinRange")
    void GivenTextThatIsNotThreeNumbersWithinRange_WhenParsed_ThenInvalidAppVersion(String text) {
        assertRefused(() -> AppVersion.parse(text), new InvalidAppVersion());
    }

    @ParameterizedTest
    @MethodSource("notThreeNumbersWithinRange")
    void GivenTextThatIsNotThreeNumbersWithinRange_WhenParsedIfValid_ThenNoVersion(String text) {
        assertThat(AppVersion.parseIfValid(text)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"1.4.2, 1, 4, 2", "0.0.2147483647, 0, 0, 2147483647"})
    void GivenThreeDotSeparatedNumbers_WhenParsedIfValid_ThenTheVersionIsGiven(
            String text, int major, int minor, int patch) {
        assertThat(AppVersion.parseIfValid(text)).contains(new AppVersion(major, minor, patch));
    }

    @Test
    void GivenNoText_WhenParsedIfValid_ThenItFailsLoudlyNamingIt() {
        assertThatThrownBy(() -> AppVersion.parseIfValid(nothing()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("text");
    }

    @ParameterizedTest
    @CsvSource({"-1, 0, 0", "0, -1, 0", "0, 0, -1"})
    void GivenANegativePart_WhenBuilt_ThenInvalidAppVersion(int major, int minor, int patch) {
        assertRefused(() -> new AppVersion(major, minor, patch), new InvalidAppVersion());
    }

    @Test
    void GivenAVersion_WhenWrittenAsText_ThenItIsTheThreeNumbersJoinedByDots() {
        assertThat(new AppVersion(3, 0, 12).text()).isEqualTo("3.0.12");
    }

    // A null that the null-marked signature doesn't admit, to test the refusal itself.
    private static String nothing() {
        return null;
    }
}
