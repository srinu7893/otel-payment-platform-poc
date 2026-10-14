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
    @Test void refreshesCacheBeforeExpiration() throws Exception {
        var seconds=new java.util.concurrent.atomic.AtomicLong(1000);
        Clock clock=new Clock() {
            public java.time.ZoneId getZone(){return ZoneOffset.UTC;}
            public Clock withZone(java.time.ZoneId zone){return this;}
            public Instant instant(){return Instant.ofEpochSecond(seconds.get());}
        };
        AtomicInteger calls=new AtomicInteger();
        var identity=new CloudRunIdentity(Set.of(AUD),audience->{calls.incrementAndGet();return token(audience,seconds.get()+120);},clock);
        identity.bearerFor(URI.create(AUD));seconds.set(1059);identity.bearerFor(URI.create(AUD));
        assertThat(calls).hasValue(1);
        seconds.set(1060);identity.bearerFor(URI.create(AUD));assertThat(calls).hasValue(2);
    }
    @Test void nonProviderAdcUsesFixedMetadataFallbackWithBoundedTimeouts() throws Exception {
        var connection=org.mockito.Mockito.mock(java.net.HttpURLConnection.class);
        org.mockito.Mockito.when(connection.getResponseCode()).thenReturn(200);
        org.mockito.Mockito.when(connection.getInputStream()).thenReturn(new java.io.ByteArrayInputStream("test-token".getBytes(StandardCharsets.UTF_8)));
        var fallback=CloudRunIdentity.metadataFetcher(url->{
            assertThat(url.toString()).isEqualTo("http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/identity?audience=https%3A%2F%2Fpayment.example.run.app&format=full");
            return connection;
        });
        try(var defaults=org.mockito.Mockito.mockStatic(com.google.auth.oauth2.GoogleCredentials.class)) {
            defaults.when(com.google.auth.oauth2.GoogleCredentials::getApplicationDefault).thenReturn(org.mockito.Mockito.mock(com.google.auth.oauth2.GoogleCredentials.class));
            assertThat(CloudRunIdentity.adcFetcher(fallback).fetch(AUD)).isEqualTo("test-token");
        }
        org.mockito.Mockito.verify(connection).setRequestMethod("GET");
        org.mockito.Mockito.verify(connection).setRequestProperty("Metadata-Flavor","Google");
        org.mockito.Mockito.verify(connection).setConnectTimeout(2000);
        org.mockito.Mockito.verify(connection).setReadTimeout(3000);
        org.mockito.Mockito.verify(connection).setInstanceFollowRedirects(false);
        org.mockito.Mockito.verify(connection).disconnect();
    }
    @Test void metadataRedirectFailsClosed() throws Exception {
        var connection=org.mockito.Mockito.mock(java.net.HttpURLConnection.class);
        org.mockito.Mockito.when(connection.getResponseCode()).thenReturn(302);
        assertThatThrownBy(()->CloudRunIdentity.metadataFetcher(url->connection).fetch(AUD)).isInstanceOf(java.io.IOException.class).hasMessage("Metadata identity request failed with HTTP 302");
        org.mockito.Mockito.verify(connection,org.mockito.Mockito.never()).getInputStream();
    }
    @Test void realComputeEngineProviderParsesMetadataIdentityResponse() throws Exception {
        String signed=Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8))+"."
            +Base64.getUrlEncoder().withoutPadding().encodeToString(("{\"aud\":\""+AUD+"\",\"exp\":"+(Instant.now().getEpochSecond()+3600)+"}").getBytes(StandardCharsets.UTF_8))+".c2ln";
        java.util.concurrent.atomic.AtomicReference<String> metadataUrl=new java.util.concurrent.atomic.AtomicReference<>();
        var transport=new com.google.api.client.testing.http.MockHttpTransport() {
            @Override public com.google.api.client.http.LowLevelHttpRequest buildRequest(String method,String url) {
                metadataUrl.set(url);
                return new com.google.api.client.testing.http.MockLowLevelHttpRequest(url)
                    .setResponse(new com.google.api.client.testing.http.MockLowLevelHttpResponse().setStatusCode(200)
                        .setContentType("application/text").addHeader("Metadata-Flavor","Google").setContent(signed));
            }
        };
        var builder=com.google.auth.oauth2.ComputeEngineCredentials.newBuilder().setHttpTransportFactory(()->transport);
        builder.setUniverseDomain("googleapis.com");
        var adc=builder.build();
        try(var defaults=org.mockito.Mockito.mockStatic(com.google.auth.oauth2.GoogleCredentials.class)) {
            defaults.when(com.google.auth.oauth2.GoogleCredentials::getApplicationDefault).thenReturn(adc);
            assertThat(new CloudRunIdentity(Set.of(AUD)).bearerFor(URI.create(AUD+"/api/v1/auth/login"))).isEqualTo("Bearer "+signed);
            assertThat(metadataUrl.get()).contains("/service-accounts/default/identity");
        }
    }
    @Test void productionFetcherUsesAdcIdTokenProviderForExactCanonicalAudience() throws Exception {
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8))+"."
            +Base64.getUrlEncoder().withoutPadding().encodeToString(("{\"aud\":\""+AUD+"\",\"exp\":"+(Instant.now().getEpochSecond()+3600)+"}").getBytes(StandardCharsets.UTF_8))+".c2ln";
        com.google.auth.oauth2.ComputeEngineCredentials adc=org.mockito.Mockito.mock(com.google.auth.oauth2.ComputeEngineCredentials.class);
        org.mockito.Mockito.when(adc.idTokenWithAudience(org.mockito.ArgumentMatchers.eq(AUD),org.mockito.ArgumentMatchers.anyList()))
            .thenReturn(com.google.auth.oauth2.IdToken.create(token));
        try(var defaults=org.mockito.Mockito.mockStatic(com.google.auth.oauth2.GoogleCredentials.class)) {
            defaults.when(com.google.auth.oauth2.GoogleCredentials::getApplicationDefault).thenReturn(adc);
            var identity=new CloudRunIdentity(Set.of(AUD));
            assertThat(identity.bearerFor(URI.create(AUD+"/api/v1/auth/login"))).isEqualTo("Bearer "+token);
            defaults.verify(com.google.auth.oauth2.GoogleCredentials::getApplicationDefault);
            org.mockito.Mockito.verify(adc).idTokenWithAudience(org.mockito.ArgumentMatchers.eq(AUD),org.mockito.ArgumentMatchers.anyList());
        }
    }
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
    @Test void gatewayStandardHttpsPortUsesPortFreeCanonicalAudience() throws Exception {
        java.util.concurrent.atomic.AtomicReference<String> requested=new java.util.concurrent.atomic.AtomicReference<>();
        var identity=new CloudRunIdentity(Set.of(AUD),audience->{requested.set(audience);return token(audience,5000);},NOW);
        assertThat(identity.bearerFor(URI.create(AUD+":443/api/v1/auth/login"))).startsWith("Bearer ");
        assertThat(requested.get()).isEqualTo(AUD);
        assertThatThrownBy(()->identity.bearerFor(URI.create(AUD+":444/api/v1/auth/login"))).isInstanceOf(java.io.IOException.class);
        assertThatThrownBy(()->identity.bearerFor(URI.create("http://payment.example.run.app:443/api"))).isInstanceOf(java.io.IOException.class);
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
