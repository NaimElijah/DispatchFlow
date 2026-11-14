package com.dispatchflow.access.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Checks {@link Capability} and {@link RoleSet#can} against the permission matrix under FR-03, as
 * {@code docs/requirements/requirements.md} states it today (ADR-104). Reading
 * the file is a test's job; the domain never reads one.
 */
class CapabilityMatrixTest {

    private static final Path REQUIREMENTS = Path.of("docs", "requirements", "requirements.md");
    private static final String HEADER = "| Capability | Admin | Dispatcher | Driver |";
    private static final String TICK = "✓";
    private static final String LIMITED_PREFIX = "Only ";
    private static final String OWN_ACCOUNT_ONLY = "Only their own";
    private static final int CELLS = 4;

    /** Each constant's row, by its capability text, which the matrix states exactly once. */
    private static final Map<Capability, String> ROWS = new EnumMap<>(Map.ofEntries(
            Map.entry(
                    Capability.MANAGE_COMPANY_AND_USERS,
                    "Manage the company's details and its users: rename it, register users, change their roles"),
            Map.entry(Capability.READ_COMPANY_AND_USERS, "Read the company's details and its users"),
            Map.entry(
                    Capability.MANAGE_FLEET_CUSTOMERS_AND_ORDERS,
                    "Manage depots, vehicles, drivers and shifts; customers and orders"),
            Map.entry(
                    Capability.PLAN_AND_RUN_THE_DAY,
                    "Plan, solve, edit, publish, cancel a published plan and re-optimize; live views and reports"),
            Map.entry(
                    Capability.WORK_OWN_ROUTE,
                    "Work their own route: read it, report stops and proofs, report positions"),
            Map.entry(
                    Capability.RECORD_OUTCOME_FOR_DRIVER,
                    "Record or correct a stop's outcome on a driver's behalf (FR-73, FR-333)"),
            Map.entry(Capability.DECIDE_HELD_REPORT, "Confirm or refuse a held backdated report (BR-132)"),
            Map.entry(Capability.DEACTIVATE_OR_REACTIVATE_USER, "Deactivate or reactivate a user (FR-53, FR-297)"),
            Map.entry(Capability.SEND_SET_PASSWORD_LINK, "Send a new user a set-password link (FR-294)"),
            Map.entry(Capability.CHANGE_USER_EMAIL, "Change a user's email (FR-387)"),
            Map.entry(
                    Capability.RETIRE_DRIVER,
                    "Retire a driver, which removes only the account's Driver role (FR-56, FR-307)"),
            Map.entry(
                    Capability.START_AND_FINISH_OWN_ROUTE,
                    "Start and finish their own route, report a problem, confirm a route change (FR-68, FR-95, FR-97)"),
            Map.entry(Capability.VIEW_PROOF_OF_DELIVERY, "View a stop's proof of delivery (FR-105)"),
            Map.entry(Capability.READ_RATINGS, "Read recipients' ratings and comments (FR-360, FR-362)"),
            Map.entry(
                    Capability.MANAGE_OPT_OUTS,
                    "See which phones opted out of messages, and record an opt-out (FR-380)"),
            Map.entry(
                    Capability.MANAGE_OWN_ACCOUNT,
                    "Change their own password and language; see and end their own sessions (FR-123, FR-124,"
                            + " FR-127)"),
            Map.entry(Capability.READ_COMPANY_SETTINGS, "Read the company's settings (FR-125)"),
            Map.entry(
                    Capability.CHANGE_COMPANY_SETTINGS,
                    "Change the company's settings, require a second factor, view audit records, end another"
                            + " user's sessions, turn messages to recipients and the rating question on or off"
                            + " (FR-125, FR-126, FR-128, FR-187, FR-350, FR-375)")));

    /** The matrix's rows as the file states them: the capability text, then one cell per role. */
    private record MatrixRow(String capability, Map<Role, Grant> cells, Map<Role, String> texts) {}

    private static List<MatrixRow> matrix;

    @BeforeAll
    static void readTheMatrix() throws IOException {
        matrix = parse(Files.readAllLines(findRequirements(), StandardCharsets.UTF_8));
        assertThat(matrix).as("the matrix's rows").hasSize(Capability.values().length);
    }

    private static Path findRequirements() {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            Path candidate = directory.resolve(REQUIREMENTS);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            directory = directory.getParent();
        }
        return fail("requirements.md is not in any folder above " + Path.of("").toAbsolutePath());
    }

    private static List<MatrixRow> parse(List<String> lines) {
        int header = lines.indexOf(HEADER);
        assertThat(header).as("the permission matrix's header").isNotNegative();
        List<MatrixRow> rows = new ArrayList<>();
        // The header is followed by the |---| separator, then one line per row until a blank line.
        for (int i = header + 2; i < lines.size() && lines.get(i).startsWith("|"); i++) {
            String line = lines.get(i).strip();
            List<String> cells = Arrays.stream(
                            line.substring(1, line.length() - 1).split("\\|", -1))
                    .map(String::strip)
                    .toList();
            assertThat(cells).as("cells of: " + line).hasSize(CELLS);
            Map<Role, Grant> grants = new EnumMap<>(Role.class);
            grants.put(Role.ADMIN, grantOf(cells.get(1)));
            grants.put(Role.DISPATCHER, grantOf(cells.get(2)));
            grants.put(Role.DRIVER, grantOf(cells.get(3)));
            Map<Role, String> texts = new EnumMap<>(Role.class);
            texts.put(Role.ADMIN, cells.get(1));
            texts.put(Role.DISPATCHER, cells.get(2));
            texts.put(Role.DRIVER, cells.get(3));
            rows.add(new MatrixRow(cells.get(0), grants, texts));
        }
        return rows;
    }

    private static Grant grantOf(String cell) {
        if (cell.isEmpty()) {
            return Grant.NONE;
        }
        if (cell.equals(TICK) || cell.startsWith(TICK + " (")) {
            return Grant.FULL;
        }
        if (cell.startsWith(LIMITED_PREFIX)) {
            return Grant.LIMITED;
        }
        return fail("a matrix cell is neither empty, a tick nor \"Only …\": " + cell);
    }

    @Test
    void GivenTheMatrix_WhenEachRowIsMatched_ThenEveryRowHasExactlyOneCapability() {
        List<String> stated = matrix.stream().map(MatrixRow::capability).toList();

        assertThat(ROWS).containsOnlyKeys(EnumSet.allOf(Capability.class));
        assertThat(Set.copyOf(ROWS.values())).hasSameSizeAs(ROWS.values());
        assertThat(stated).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(ROWS.values());
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void GivenOneRole_WhenAskedAboutEveryCapability_ThenTheAnswerIsItsColumnOfTheMatrix(Role role) {
        Map<String, Capability> byRow =
                ROWS.entrySet().stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

        for (MatrixRow row : matrix) {
            Capability capability = byRow.get(row.capability());
            Grant expected = row.cells().get(role);
            assertThat(capability.grantTo(role)).as(capability + " for " + role).isEqualTo(expected);
            assertThat(RoleSet.of(role).grant(capability))
                    .as(capability + " for " + role)
                    .isEqualTo(expected);
            assertThat(RoleSet.of(role).can(capability))
                    .as(capability + " for " + role)
                    .isEqualTo(expected != Grant.NONE);
        }
    }

    @Test
    void GivenEveryCombinationOfRoles_WhenAskedAboutEveryCapability_ThenTheAnswerIsTheUnionOfItsCells() {
        Map<String, Capability> byRow = capabilitiesByRow();
        for (Set<Role> combination : nonEmptyCombinations()) {
            RoleSet roles = RoleSet.of(combination);
            for (MatrixRow row : matrix) {
                Capability capability = byRow.get(row.capability());
                Grant expected = unionOfCells(row, combination);
                assertThat(roles.grant(capability))
                        .as(capability + " for " + combination)
                        .isEqualTo(expected);
                assertThat(roles.can(capability))
                        .as(capability + " for " + combination)
                        .isEqualTo(expected != Grant.NONE);
            }
        }
    }

    /** The union of a row's cells for some roles, read from the cells alone: a tick beats "Only". */
    private static Grant unionOfCells(MatrixRow row, Set<Role> combination) {
        List<Grant> cells =
                combination.stream().map(role -> row.cells().get(role)).toList();
        if (cells.contains(Grant.FULL)) {
            return Grant.FULL;
        }
        return cells.contains(Grant.LIMITED) ? Grant.LIMITED : Grant.NONE;
    }

    @Test
    void GivenADispatcherWhoDrives_WhenAskedToReadTheCompanyAndItsUsers_ThenTheDispatchersFullGrantWins() {
        assertThat(RoleSet.of(Role.DISPATCHER, Role.DRIVER).grant(Capability.READ_COMPANY_AND_USERS))
                .isEqualTo(Grant.FULL);
    }

    @Test
    void GivenTheMatrix_WhenAdminOnlyRowsAreListed_ThenTheyAreTheRowsWithOnlyTheAdminCellTicked() {
        Map<String, Capability> byRow = capabilitiesByRow();
        Set<Capability> fromTheFile = matrix.stream()
                .filter(row -> row.cells().get(Role.ADMIN) != Grant.NONE
                        && row.cells().get(Role.DISPATCHER) == Grant.NONE
                        && row.cells().get(Role.DRIVER) == Grant.NONE)
                .map(row -> byRow.get(row.capability()))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Capability.class)));
        Set<Capability> adminOnly = EnumSet.allOf(Capability.class);
        adminOnly.removeIf(capability -> !capability.isAdminOnly());

        assertThat(adminOnly)
                .isEqualTo(fromTheFile)
                .containsExactlyInAnyOrder(
                        Capability.MANAGE_COMPANY_AND_USERS,
                        Capability.DEACTIVATE_OR_REACTIVATE_USER,
                        Capability.SEND_SET_PASSWORD_LINK,
                        Capability.CHANGE_COMPANY_SETTINGS);
    }

    /**
     * NFR-214's step-up set: the Admin-only rows, and the rows whose every non-Admin cell is empty or
     * "Only their own", when used on another account. Read from the cells' text alone.
     */
    @Test
    void GivenTheMatrix_WhenStepUpIsAskedFor_ThenItIsTheAdminOnlyRowsAndTheOwnAccountRowsUsedOnAnother() {
        Map<String, Capability> byRow = capabilitiesByRow();
        Set<Capability> ownAccountOnly = EnumSet.noneOf(Capability.class);
        for (MatrixRow row : matrix) {
            List<String> others =
                    List.of(row.texts().get(Role.DISPATCHER), row.texts().get(Role.DRIVER));
            boolean onlyOwn = row.cells().get(Role.ADMIN) == Grant.FULL
                    && others.stream().allMatch(cell -> cell.isEmpty() || cell.equals(OWN_ACCOUNT_ONLY))
                    && others.contains(OWN_ACCOUNT_ONLY);
            if (onlyOwn) {
                ownAccountOnly.add(byRow.get(row.capability()));
            }
        }
        assertThat(ownAccountOnly).containsExactly(Capability.CHANGE_USER_EMAIL);

        for (Capability capability : Capability.values()) {
            assertThat(capability.requiresStepUp(true))
                    .as(capability + " on the caller's own account")
                    .isEqualTo(capability.isAdminOnly());
            assertThat(capability.requiresStepUp(false))
                    .as(capability + " on another account")
                    .isEqualTo(capability.isAdminOnly() || ownAccountOnly.contains(capability));
        }
    }

    @Test
    void GivenAnAdminChangingAnotherUsersEmail_WhenStepUpIsAskedFor_ThenItIsRequiredButNotForTheirOwn() {
        assertThat(Capability.CHANGE_USER_EMAIL.requiresStepUp(false)).isTrue();
        assertThat(Capability.CHANGE_USER_EMAIL.requiresStepUp(true)).isFalse();
        assertThat(Capability.READ_COMPANY_AND_USERS.requiresStepUp(false)).isFalse();
    }

    private static Map<String, Capability> capabilitiesByRow() {
        return ROWS.entrySet().stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
    }

    @Test
    void GivenNoRole_WhenAskedAboutEveryCapability_ThenNothingIsAllowed() {
        for (Capability capability : Capability.values()) {
            assertThat(RoleSet.of().can(capability)).as(capability.name()).isFalse();
            assertThat(RoleSet.of().grant(capability)).as(capability.name()).isEqualTo(Grant.NONE);
        }
    }

    private static List<Set<Role>> nonEmptyCombinations() {
        Role[] roles = Role.values();
        List<Set<Role>> combinations = new ArrayList<>();
        for (int mask = 1; mask < 1 << roles.length; mask++) {
            Set<Role> combination = EnumSet.noneOf(Role.class);
            for (int bit = 0; bit < roles.length; bit++) {
                if ((mask & (1 << bit)) != 0) {
                    combination.add(roles[bit]);
                }
            }
            combinations.add(combination);
        }
        return combinations;
    }
}
