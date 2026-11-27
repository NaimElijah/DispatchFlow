package com.dispatchflow;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Refuses to boot the core on a database session it must never run on (ADR-151). It checks once,
 * at startup, on the core's own connection, even with {@code spring.main.lazy-initialization} on,
 * which would otherwise create it only when another bean asked for it, and none does:
 *
 * <ul>
 *   <li>the session's role is the one it logged in as: a login that switches to {@code app} with
 *       {@code SET ROLE} can switch back;
 *   <li>the core's role has none of the attributes that ADR-145's roles leave off and that pass
 *       every grant: {@code SUPERUSER}, {@code CREATEDB}, {@code CREATEROLE}, {@code REPLICATION}
 *       and {@code BYPASSRLS};
 *   <li>it is a member of no other role. A member can take a role's rights with {@code SET ROLE},
 *       and PostgreSQL records no owner for the superuser or a predefined role such as {@code
 *       pg_write_all_data}, so only membership itself shows that;
 *   <li>it owns no object, in any database. An owner can grant itself back whatever a migration
 *       revoked (ADR-041, ADR-145);
 *   <li>the server writes its messages in English, because Hibernate reads a violated
 *       constraint's name from the message text, and four of the core's answers to a race depend
 *       on it (#250).
 * </ul>
 *
 * <p>So a credential mapped to the wrong Compose service, or a deployment that connects the core as
 * the owner, the superuser or a member of either, stops the core before it serves a request. The
 * messages name the role, the roles it belongs to and the setting, never a password. {@code
 * DatabaseSessionGuardTest} proves each refusal, and {@code DatabaseSessionGuardIT} that the
 * session is read right and the guard runs at startup.
 *
 * <p>Every context the core's component scan builds has it, but the {@value #SCHEMA_DRAFT}
 * profile's, which opens no connection. The migrate command's context is never scanned: it
 * connects as the owner by design.
 */
@Component
@Lazy(false)
@Profile("!" + DatabaseSessionGuard.SCHEMA_DRAFT)
class DatabaseSessionGuard {

    /** The profile of {@code just core-schema-draft}, which writes DDL and opens no connection. */
    static final String SCHEMA_DRAFT = "schema-draft";

    /**
     * The role's attributes, the roles it is a member of, directly or through another, and whether
     * it owns an object in any database: {@code pg_shdepend} is shared by the whole server.
     */
    private static final String SESSION_ROLE = """
            SELECT session_user AS login_role, r.rolname AS role, r.rolsuper AS superuser, r.rolcreatedb AS createdb,
                   r.rolcreaterole AS createrole, r.rolreplication AS replication, r.rolbypassrls AS bypassrls,
                   (SELECT pg_catalog.string_agg(g.rolname, ', ' ORDER BY g.rolname)
                    FROM pg_catalog.pg_roles g
                    WHERE g.oid <> r.oid AND pg_catalog.pg_has_role(r.oid, g.oid, 'MEMBER')) AS member_of,
                   EXISTS (SELECT 1 FROM pg_catalog.pg_shdepend d
                           WHERE d.refclassid = 'pg_catalog.pg_authid'::pg_catalog.regclass
                             AND d.refobjid = r.oid
                             AND d.deptype = 'o') AS owns,
                   pg_catalog.current_setting('lc_messages') AS lc_messages
            FROM pg_catalog.pg_roles r
            WHERE r.rolname = current_user
            """;

    /**
     * Checks the core's session once, at startup.
     *
     * @param jdbc the core's own connection
     * @throws IllegalStateException naming the role and what is wrong with its session, when any
     *     refusal applies
     */
    DatabaseSessionGuard(JdbcClient jdbc) {
        check(jdbc);
    }

    /**
     * Reads the session on the given connection and refuses it if the core must not run on it.
     *
     * @param jdbc the connection to check
     * @throws IllegalStateException naming the role and what is wrong with its session, when any
     *     refusal applies
     */
    static void check(JdbcClient jdbc) {
        Session session = jdbc.sql(SESSION_ROLE).query(Session.class).single();
        Optional<String> refusal = session.refusal();
        if (refusal.isPresent()) {
            throw new IllegalStateException(refusal.get());
        }
    }

    /**
     * Whether a value of {@code lc_messages} gives English messages: {@code C}, {@code POSIX}, or an
     * English locale. An empty value inherits the server's environment, which the core can't see,
     * so it doesn't count.
     *
     * @param lcMessages the setting, as the server reports it
     * @return whether the server's messages are English
     */
    static boolean readsEnglish(String lcMessages) {
        String setting = lcMessages.toLowerCase(Locale.ROOT);
        return setting.equals("c")
                || setting.startsWith("c.")
                || setting.equals("posix")
                || setting.equals("en")
                || setting.startsWith("en_")
                || setting.startsWith("en.");
    }

    /**
     * What the guard reads of a session, one row of {@link #SESSION_ROLE}.
     *
     * @param loginRole the role the session logged in as, {@code session_user}
     * @param role the session's role, {@code current_user}
     * @param memberOf the other roles it is a member of, comma-separated, or null for none
     * @param owns whether it owns an object in any database
     * @param lcMessages the server's {@code lc_messages} in this session
     */
    record Session(
            String loginRole,
            String role,
            boolean superuser,
            boolean createdb,
            boolean createrole,
            boolean replication,
            boolean bypassrls,
            @Nullable String memberOf,
            boolean owns,
            String lcMessages) {

        /**
         * Why the core must not run on this session, the first reason found, or empty when it may.
         *
         * @return the refusal's message, which names the role and the setting and nothing secret
         */
        Optional<String> refusal() {
            if (!loginRole.equals(role)) {
                return Optional.of("The core's database connection logs in as " + loginRole + " and switches to " + role
                        + ": it must log in as its own role, which can't switch back (ADR-145, ADR-151)");
            }
            List<String> attributes = attributesThatPassTheGrants();
            if (!attributes.isEmpty()) {
                return Optional.of("The core's database role " + role + " has " + String.join(", ", attributes)
                        + ": it must have none of them (ADR-145, ADR-151)");
            }
            if (memberOf != null) {
                return Optional.of("The core's database role " + role + " is a member of " + memberOf
                        + ": it must belong to no other role, whose rights it could take (ADR-145, ADR-151)");
            }
            if (owns) {
                return Optional.of("The core's database role " + role
                        + " owns an object: the core connects as app, which owns nothing (ADR-145, ADR-151)");
            }
            if (!readsEnglish(lcMessages)) {
                return Optional.of("The database server's lc_messages is '" + lcMessages
                        + "': set it to C or an English locale, such as en_US.UTF-8 (#250, ADR-151)");
            }
            return Optional.empty();
        }

        private List<String> attributesThatPassTheGrants() {
            List<String> attributes = new ArrayList<>();
            if (superuser) {
                attributes.add("SUPERUSER");
            }
            if (createdb) {
                attributes.add("CREATEDB");
            }
            if (createrole) {
                attributes.add("CREATEROLE");
            }
            if (replication) {
                attributes.add("REPLICATION");
            }
            if (bypassrls) {
                attributes.add("BYPASSRLS");
            }
            return List.copyOf(attributes);
        }
    }
}
