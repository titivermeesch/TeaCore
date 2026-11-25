package me.playbosswar.tea.core.impl.coordination;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MessageSigningTest {

    private static final byte[] SECRET = "s3cret".getBytes(StandardCharsets.UTF_8);
    private static final String CHANNEL = "tea:control:arena-1";
    private static final String BODY = "{\"type\":\"APPLY_SETTINGS\",\"mode\":\"a|b\"}";

    @Test
    void signedMessageOpens() {
        String sealed = RedisBackend.seal(SECRET, CHANNEL, BODY, 1_000_000L);
        assertThat(RedisBackend.open(SECRET, CHANNEL, sealed, 1_000_500L)).isEqualTo(BODY);
    }

    @Test
    void unsignedOrForgedMessagesAreDropped() {
        String sealed = RedisBackend.seal(SECRET, CHANNEL, BODY, 1_000_000L);
        assertThat(RedisBackend.open(SECRET, CHANNEL, BODY, 1_000_000L)).isNull();
        assertThat(RedisBackend.open("other".getBytes(StandardCharsets.UTF_8), CHANNEL, sealed, 1_000_000L)).isNull();
        assertThat(RedisBackend.open(SECRET, "tea:control:arena-2", sealed, 1_000_000L)).isNull();
        assertThat(RedisBackend.open(SECRET, CHANNEL, sealed.replace("APPLY", "APPLx"), 1_000_000L)).isNull();
        assertThat(RedisBackend.open(SECRET, CHANNEL, null, 1_000_000L)).isNull();
    }

    @Test
    void staleMessagesAreDropped() {
        String sealed = RedisBackend.seal(SECRET, CHANNEL, BODY, 1_000_000L);
        assertThat(RedisBackend.open(SECRET, CHANNEL, sealed,
                1_000_000L + RedisBackend.MAX_MESSAGE_AGE_MS + 1)).isNull();
    }

    @Test
    void replayedMessagesAreDropped() {
        RedisBackend.ReplayGuard guard = new RedisBackend.ReplayGuard();
        String sealed = RedisBackend.seal(SECRET, CHANNEL, BODY, 1_000_000L);
        assertThat(RedisBackend.open(SECRET, CHANNEL, sealed, 1_000_100L, guard)).isEqualTo(BODY);
        assertThat(RedisBackend.open(SECRET, CHANNEL, sealed, 1_000_200L, guard)).isNull();

        String again = RedisBackend.seal(SECRET, CHANNEL, BODY, 1_000_000L);
        assertThat(again).isNotEqualTo(sealed);
        assertThat(RedisBackend.open(SECRET, CHANNEL, again, 1_000_300L, guard)).isEqualTo(BODY);
    }

    @Test
    void seenNoncesAreForgottenOnceTheirMessagesExpire() {
        RedisBackend.ReplayGuard guard = new RedisBackend.ReplayGuard();
        String sealed = RedisBackend.seal(SECRET, CHANNEL, BODY, 1_000_000L);
        assertThat(RedisBackend.open(SECRET, CHANNEL, sealed, 1_000_000L, guard)).isEqualTo(BODY);
        long later = 1_000_000L + RedisBackend.MAX_MESSAGE_AGE_MS + 60_000;
        assertThat(RedisBackend.open(SECRET, CHANNEL, RedisBackend.seal(SECRET, CHANNEL, BODY, later), later, guard))
                .isEqualTo(BODY);
        assertThat(guard.size()).isEqualTo(1);
        assertThat(RedisBackend.open(SECRET, CHANNEL, sealed, later, guard)).isNull();
    }

    @Test
    void tamperedNonceFailsTheSignature() {
        String sealed = RedisBackend.seal(SECRET, CHANNEL, BODY, 1_000_000L);
        String[] parts = sealed.split("\\|", 5);
        String swapped = parts[0] + "|" + parts[1] + "|" + "0".repeat(32) + "|" + parts[3] + "|" + parts[4];
        assertThat(RedisBackend.open(SECRET, CHANNEL, swapped, 1_000_000L)).isNull();
    }
}
