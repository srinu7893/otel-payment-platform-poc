package com.srinu.otelpoc.cloud;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
class CloudRunIdentityTest {
    private static final String AUD = "https://payment.example.run.app";
    private static final Clock NOW = Clock.fixed(Instant.ofEpochSecond(1000), ZoneOffset.UTC);
    private String token(String aud, long expiry) {
        return "header." + Base64.getUrlEncoder().withoutPadding().encodeToString(
            ("{\"aud\":\"" + aud + "\",\"exp\":" + expiry + "}").getBytes(StandardCharsets.UTF_8)) + ".signature";
    }
    @Test void cachesValidTokenAndUsesServiceOriginDespiteRequestPath() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var identity = new CloudRunIdentity(Set.of(AUD), audience -> { calls.incrementAndGet(); return token(audience, 5000); }, NOW);
        assertThat(identity.bearerFor(URI.create(AUD + "/api/payments?x=1"))).startsWith("Bearer ");
        identity.bearerFor(URI.create(AUD + "/other"));
        assertThat(calls).hasValue(1);
    }
    @Test void rejectsUnlistedHostAndUserInfoWithoutFetching() {
        var identity = new CloudRunIdentity(Set.of(AUD), audience -> { throw new AssertionError("Must not fetch"); }, NOW);
        assertThatThrownBy(() -> identity.bearerFor(URI.create("https://evil.example/api"))).isInstanceOf(java.io.IOException.class);
        assertThatThrownBy(() -> identity.bearerFor(URI.create("https://user@payment.example.run.app/api"))).isInstanceOf(java.io.IOException.class);
    }
    @Test void failsClosedForExpiredOrWrongAudienceTokens() {
        for (String invalid : new String[]{token(AUD, 1030), token("https://other.run.app", 5000), "malformed"}) {
            var identity = new CloudRunIdentity(Set.of(AUD), audience -> invalid, NOW);
            assertThatThrownBy(() -> identity.bearerFor(URI.create(AUD))).isInstanceOf(java.io.IOException.class);
        }
    }
    @Test void rejectsNonHttpsAudienceConfiguration() {
        assertThatThrownBy(() -> new CloudRunIdentity(Set.of("http://localhost:8080"))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void preservesInterruptOnMetadataFailure() {
        var identity = new CloudRunIdentity(Set.of(AUD), audience -> { throw new InterruptedException(); }, NOW);
        try {
            assertThatThrownBy(() -> identity.bearerFor(URI.create(AUD))).isInstanceOf(java.io.IOException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally { Thread.interrupted(); }
    }
}
