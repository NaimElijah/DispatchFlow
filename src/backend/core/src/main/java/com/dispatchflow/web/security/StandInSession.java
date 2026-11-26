package com.dispatchflow.web.security;

import com.dispatchflow.StandInOnly;
import com.dispatchflow.StandInProfiles;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.SessionId;
import com.dispatchflow.shared.adapter.in.web.FilterOrder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The stand-in session: until sign-in is built, every request that presents no credential is
 * signed in as the entry of the shared list of seeded accounts that {@value #ACCOUNT} names, the
 * Dispatcher wherever a run sets it, with the fixed session {@link #SESSION} (ADR-121 decision 5, ADR-140
 * decision 4, ADR-147). So the calls built before sign-in, which take an {@code Actor}, run, and
 * their audit records name an account and a session (NFR-93).
 *
 * <p>It fails closed. It exists only when the active profiles are exactly {@code dev} or exactly
 * {@code test} ({@link StandInOnly}), and {@link StandInGuard} refuses to boot with its
 * configuration otherwise, or in a deployed environment. A request that presents a credential, an
 * {@code Authorization} header or the refresh cookie {@value #REFRESH_COOKIE}, is left without a
 * principal, so a presented token that fails answers 401 under every profile; until #199 builds the
 * token filter, every presented token fails. A request to another host than the loopback names, or
 * one a browser marks cross-site, is left without a principal too. The main classpath's {@code dev}
 * file names no account, so a deployment that runs {@code dev} by mistake refuses to boot. Building
 * sign-in deletes this class.
 */
@Component
@StandInOnly
@Order(StandInSession.ORDER)
class StandInSession extends OncePerRequestFilter {

    /** The property that names the entry the stand-in acts as. */
    static final String ACCOUNT = StandInProfiles.STAND_IN + ".account";

    /**
     * The refresh token's cookie (ADR-069): a {@code __Host-} cookie, sent on every request to the
     * host, which only the refresh and sign-out endpoints read. #199 sets and reads it under this
     * name.
     */
    static final String REFRESH_COOKIE = "__Host-refresh";

    /**
     * The stand-in's session. It names no stored session: no session row exists for it, and the
     * audit record keeps a session's ID without a foreign key.
     */
    static final SessionId SESSION = SessionId.of(UUID.fromString("0198c3a0-5d1e-7a00-8000-5e5510000001"));

    /** After {@code web}'s trace filter, so a failure here is traced ({@link FilterOrder}). */
    static final int ORDER = FilterOrder.PRINCIPAL;

    // The host names a request to the loopback interface carries; an IPv6 literal may come bracketed.
    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");
    private static final String SEC_FETCH_SITE = "Sec-Fetch-Site";
    private static final String CROSS_SITE = "cross-site";

    private final SignedInCaller standIn;

    /**
     * Reads the entry the stand-in acts as.
     *
     * @param environment the environment, with the shared list imported and {@value #ACCOUNT} set
     * @throws IllegalStateException when {@link StandInGuard#check} refuses the environment, or
     *     {@value #ACCOUNT} is unset or names no entry on the list
     */
    StandInSession(Environment environment) {
        StandInGuard.check(environment);
        String name = environment.getProperty(ACCOUNT);
        if (name == null || name.isBlank()) {
            throw new IllegalStateException(ACCOUNT + " is unset, so the stand-in session acts as no account: the"
                    + " tests and just core-run set it, and the Compose core sets DISPATCHFLOW_STANDIN_ACCOUNT; no"
                    + " other run may use the " + StandInProfiles.DEV + " profile");
        }
        SeededAccounts.Entry entry = SeededAccounts.from(environment).entries().get(name);
        if (entry == null) {
            throw new IllegalStateException(ACCOUNT + " names " + name + ", which is no entry of the seeded accounts");
        }
        this.standIn = new SignedInCaller(AccountId.of(entry.id()), SESSION, entry.roles());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!presentsACredential(request) && isFromThisMachinesOwnPages(request)) {
            standIn.signIn(request);
        }
        chain.doFilter(request, response);
    }

    /**
     * Whether the request came to this machine's loopback name, and not from another site's page. A
     * page in the developer's browser could otherwise act as the Dispatcher: by a cross-site request
     * that needs no preflight, which its browser marks {@code Sec-Fetch-Site: cross-site}, or by
     * rebinding its own host name to the loopback address, which leaves its own name in {@code Host}.
     * A client that sends no {@code Sec-Fetch-Site}, such as {@code curl}, is no page.
     */
    private static boolean isFromThisMachinesOwnPages(HttpServletRequest request) {
        return LOOPBACK_HOSTS.contains(request.getServerName())
                && !CROSS_SITE.equals(request.getHeader(SEC_FETCH_SITE));
    }

    private static boolean presentsACredential(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        return request.getHeader(HttpHeaders.AUTHORIZATION) != null
                || (cookies != null
                        && Arrays.stream(cookies).anyMatch(cookie -> REFRESH_COOKIE.equals(cookie.getName())));
    }
}
