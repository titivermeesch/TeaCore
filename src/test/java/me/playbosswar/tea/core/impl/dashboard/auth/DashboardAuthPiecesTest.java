package me.playbosswar.tea.core.impl.dashboard.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DashboardAuthPiecesTest {

    @Test
    void codesAreReadableAndNormalizeLoosely() {
        LoginCodes codes = new LoginCodes(null, null);
        String code = codes.newCode();
        assertThat(code).matches("[A-HJ-KM-NP-Z2-9]{4}-[A-HJ-KM-NP-Z2-9]{4}");
        assertThat(LoginCodes.normalize(" k7qp-m3xd ")).isEqualTo("K7QPM3XD");
    }

    @Test
    void grantedKeepsOnlyHeldNodesInOrder() {
        assertThat(LoginCodes.granted(n -> n.equals(DashboardPermission.VIEW) || n.equals(DashboardPermission.SESSIONS)))
                .containsExactly(DashboardPermission.VIEW, DashboardPermission.SESSIONS);
    }

    @Test
    void sessionsCanBeFoundAndRevoked() {
        DashboardSessions sessions = new DashboardSessions();
        DashboardSessions.Session s = sessions.create(UUID.randomUUID(), "Alice", Set.of(DashboardPermission.VIEW), "1.2.3.4");
        assertThat(s.token()).hasSizeGreaterThan(40);
        assertThat(sessions.get(s.token())).contains(s);
        assertThat(sessions.get("nope")).isEmpty();
        assertThat(sessions.get(null)).isEmpty();
        assertThat(s.can(DashboardPermission.VIEW)).isTrue();
        assertThat(s.can(DashboardPermission.ARENA_END)).isFalse();
        assertThat(sessions.revoke(s.id())).isTrue();
        assertThat(sessions.get(s.token())).isEmpty();
    }

    @Test
    void bearerAdminTokenGetsEveryPermission() {
        DashboardAuth auth = new DashboardAuth(new DashboardSessions(), null, "secret", null);
        assertThat(auth.resolve(null, "Bearer secret")).hasValueSatisfying(s ->
                assertThat(s.permissions()).containsAll(DashboardPermission.ALL));
        assertThat(auth.resolve(null, "Bearer wrong")).isEmpty();
        DashboardAuth off = new DashboardAuth(new DashboardSessions(), null, "", null);
        assertThat(off.resolve(null, "Bearer ")).isEmpty();
    }
}
