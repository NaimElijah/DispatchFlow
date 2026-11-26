package com.dispatchflow.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.access.application.api.ActorRole;
import com.dispatchflow.audit.application.api.ClientKind;
import com.dispatchflow.audit.application.api.ClientRelease;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.adapter.in.web.Problems;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;

class CallerTest {

    private static final String TRACE = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final SignedInCaller DISPATCHER = new SignedInCaller(
            AccountId.of(UUID.fromString("0190a5e2-7c3b-7def-8a12-0000000000a1")),
            StandInSession.SESSION,
            Set.of(ActorRole.DISPATCHER));

    @BeforeEach
    void traceTheCall() {
        MDC.put(Problems.TRACE_ID, TRACE);
    }

    @AfterEach
    void clearTheTrace() {
        MDC.clear();
    }

    private static MockHttpServletRequest declaring(String app, String version) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(Caller.X_APP, app);
        request.addHeader(Caller.X_APP_VERSION, version);
        return request;
    }

    @Test
    void GivenXAppPhoneAndAVersion_WhenTheClientIsRead_ThenItIsThePhoneRelease() {
        assertThat(Caller.clientOf(declaring("phone", "2.0.1"))).contains(new ClientRelease(ClientKind.PHONE, "2.0.1"));
    }

    @Test
    void GivenXAppInCapitals_WhenTheClientIsRead_ThenItIsLeftOut() {
        assertThat(Caller.clientOf(declaring("WEB", "1.4.0"))).isEmpty();
    }

    @Test
    void GivenNoXAppVersion_WhenTheClientIsRead_ThenItIsLeftOut() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(Caller.X_APP, "web");

        assertThat(Caller.clientOf(request)).isEmpty();
    }

    @Test
    void GivenNoXApp_WhenTheClientIsRead_ThenItIsLeftOut() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(Caller.X_APP_VERSION, "1.4.0");

        assertThat(Caller.clientOf(request)).isEmpty();
    }

    @Test
    void GivenTwoXAppHeaders_WhenTheClientIsRead_ThenItIsLeftOut() {
        MockHttpServletRequest request = declaring("web", "1.4.0");
        request.addHeader(Caller.X_APP, "phone");

        assertThat(Caller.clientOf(request)).isEmpty();
    }

    @Test
    void GivenTwoAppsInOneXAppHeader_WhenTheClientIsRead_ThenItIsLeftOut() {
        assertThat(Caller.clientOf(declaring("web, phone", "1.4.0"))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.4.0-beta", "1.4", "v1.4.0", "1.4.0 ", "1234567890.0.0", "<script>"})
    void GivenAVersionThatIsNoReleaseNumber_WhenTheClientIsRead_ThenItIsLeftOut(String version) {
        assertThat(Caller.clientOf(declaring("web", version))).isEmpty();
    }

    @Test
    void GivenASignedInPrincipal_WhenTheCallerIsBuilt_ThenItsActorCarriesTheSameCall() {
        Caller caller = Caller.build(new MockHttpServletRequest(), Optional.of(DISPATCHER), "publishPlan");

        assertThat(caller.actor()).hasValueSatisfying(actor -> {
            assertThat(actor.account()).isEqualTo(DISPATCHER.account());
            assertThat(actor.roles()).containsExactly(ActorRole.DISPATCHER);
            assertThat(actor.call()).isSameAs(caller.call());
        });
        assertThat(caller.call().operation()).isEqualTo("publishPlan");
        assertThat(caller.call().traceId()).isEqualTo(TRACE);
    }

    @Test
    void GivenNoPrincipal_WhenTheCallerIsBuilt_ThenItHasNoActorAndItsCallNamesNoUserOrSession() {
        Caller caller = Caller.build(new MockHttpServletRequest(), Optional.empty(), "publishPlan");

        assertThat(caller.actor()).isEmpty();
        assertThat(caller.call().user()).isEmpty();
        assertThat(caller.call().session()).isEmpty();
    }

    @Test
    void GivenNoTraceIdInTheMdc_WhenTheCallerIsBuilt_ThenItFailsLoudly() {
        MDC.clear();

        assertThatThrownBy(() -> Caller.build(new MockHttpServletRequest(), Optional.empty(), "publishPlan"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("trace filter");
    }

    @Test
    void GivenAnActorOfAnotherCall_WhenACallerPairsThem_ThenItIsRefused() {
        Caller signedIn = Caller.build(new MockHttpServletRequest(), Optional.of(DISPATCHER), "publishPlan");
        Caller other = Caller.build(new MockHttpServletRequest(), Optional.of(DISPATCHER), "openPlan");

        assertThatThrownBy(() -> new Caller(other.call(), signedIn.actor()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("another call");
    }

    @Test
    void GivenASignedInCaller_WhenItIsKeptForTheRequest_ThenTheMdcNamesItsAccountAndSession() {
        Caller.build(new MockHttpServletRequest(), Optional.of(DISPATCHER), "publishPlan")
                .keepFor(new MockHttpServletRequest());

        assertThat(MDC.get(Caller.ACCOUNT_ID))
                .isEqualTo(DISPATCHER.account().value().toString());
        assertThat(MDC.get(Caller.SESSION_ID))
                .isEqualTo(StandInSession.SESSION.value().toString());
    }

    @Test
    void GivenACallerWithNoPrincipal_WhenItIsKeptForTheRequest_ThenTheMdcNamesNoAccountOrSession() {
        Caller.build(new MockHttpServletRequest(), Optional.empty(), "publishPlan")
                .keepFor(new MockHttpServletRequest());

        assertThat(MDC.get(Caller.ACCOUNT_ID)).isNull();
        assertThat(MDC.get(Caller.SESSION_ID)).isNull();
    }

    @Test
    void GivenARequestTheInterceptorNeverSaw_WhenItsCallerIsRead_ThenThereIsNone() {
        assertThat(Caller.of(new MockHttpServletRequest())).isEmpty();
    }
}
