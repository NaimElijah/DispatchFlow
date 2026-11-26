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
import java.util.Locale;
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
 * Dispatcher wherever a run sets it, with the fixed session {@link #SESSION} (ADR-121 decision 5,
 * ADR-140 decision 4, ADR-147, ADR-148). So the calls built before sign-in, which take an {@code
 * Actor}, run, and their audit records name an account and a session (NFR-93).
 *
 * <p>It fails closed. It exists only when the active profiles are exactly {@code dev} or exactly
 * {@code test} ({@link StandInOnly}), and {@link StandInGuard} refuses to boot with its
 * configuration otherwise, or in a deployed environment. A request that presents a credential, an
 * {@code Authorization} header or the refresh cookie {@value #REFRESH_COOKIE}, is left without a
 * principal, so a presented token that fails answers 401 under every profile; until #199 builds the
 * token filter, every presented token fails. A request to another host than the loopback names, or
 * one a browser sent from another origin's page, is left without a principal too. The main
 * classpath's {@code dev} file names no account, so a deployment that runs {@code dev} by mistake
 * refuses to boot. Building sign-in deletes this class, but not the guard, which every other
 * stand-in still needs (ADR-148).
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

    // The loopback names a Host header carries, in lower case; an IPv6 literal comes bracketed.
    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");
    private static final String SEC_FETCH_SITE = "Sec-Fetch-Site";
    // A browser sends same-origin from the core's own origin, which the web app reaches it through,
    // and none for a request the user made, such as a typed address (Fetch Metadata).
    private static final Set<String> OWN_PAGES = Set.of("same-origin", "none");

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
                    + " other run may use the profile " + Arrays.asList(environment.getActiveProfiles()));
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
     * Whether the request came to this machine's loopback name, from no other origin's page. A page
     * in the developer's browser could otherwise act as the Dispatcher: by a request that needs no
     * preflight, from another site or from another port of {@code localhost}, which its browser
     * marks {@code Sec-Fetch-Site} {@code cross-site} or {@code same-site}; or by rebinding its own
     * host name to the loopback address. A rebinding page calls its own {@code http://} name, which
     * is no potentially trustworthy URL, so its browser sends no {@code Sec-Fetch-Site}, and only the
     * {@code Host} check refuses it. A request with no {@code Sec-Fetch-Site} is otherwise a client's
     * that is no browser, such as {@code curl} or the phone app, and is served.
     */
    private static boolean isFromThisMachinesOwnPages(HttpServletRequest request) {
        String site = request.getHeader(SEC_FETCH_SITE);
        return LOOPBACK_HOSTS.contains(hostNameOf(request)) && (site == null || OWN_PAGES.contains(site));
    }

    /**
     * The host name the client sent, from the {@code Host} header itself, never {@code
     * getServerName()}: forwarded-header handling, Tomcat's {@code RemoteIpValve} or Spring's {@code
     * ForwardedHeaderFilter}, rewrites the server name from {@code X-Forwarded-Host} or {@code
     * Forwarded}, which any client may send. A host name is case-insensitive (RFC 9110). A request
     * with no {@code Host} header falls back to the server name: MockMvc's requests carry none, so
     * every {@code @IntegrationTest} reaches the stand-in this way. It is no browser's, since every
     * browser sends one and Tomcat refuses an HTTP/1.1 request without it, and a client that omits it
     * could as well send {@code Host: localhost}.
     */
    private static String hostNameOf(HttpServletRequest request) {
        String host = request.getHeader(HttpHeaders.HOST);
        String named = host == null ? request.getServerName() : withoutPort(host.strip());
        return named.toLowerCase(Locale.ROOT);
    }

    private static String withoutPort(String host) {
        int end = host.startsWith("[") ? host.indexOf(']') + 1 : host.indexOf(':');
        return end <= 0 ? host : host.substring(0, end);
    }

    private static boolean presentsACredential(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        return request.getHeader(HttpHeaders.AUTHORIZATION) != null
                || (cookies != null
                        && Arrays.stream(cookies).anyMatch(cookie -> REFRESH_COOKIE.equals(cookie.getName())));
    }
}
