package com.srinu.otelpoc.cloud;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenProvider;
import com.google.auth.oauth2.IdTokenCredentials;
import java.net.URI;
import java.time.Clock;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Only fixed, deployment-configured service origins can receive workload credentials. */
public final class CloudRunIdentity {
    public static final String HEADER = "X-Serverless-Authorization";
    private final Set<String> audiences;
    private final TokenFetcher fetcher;
    private final Clock clock;
    private final Map<String, Cached> cache = new HashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    @FunctionalInterface public interface TokenFetcher { String fetch(String audience) throws IOException, InterruptedException; }
    private record Cached(String token, long expires) {}

    public CloudRunIdentity(Set<String> audiences) {
        this(audiences, adcFetcher(), Clock.systemUTC());
    }
    public CloudRunIdentity(Set<String> audiences, TokenFetcher fetcher, Clock clock) {
        this.audiences = audiences.stream().map(value -> {
            URI uri = URI.create(value);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getPort() != -1 || uri.getRawQuery() != null || uri.getFragment() != null
                || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) {
                throw new IllegalArgumentException("Cloud Run audiences must be HTTPS service origins");
            }
            return "https://" + uri.getHost();
        }).collect(Collectors.toUnmodifiableSet());
        if (this.audiences.isEmpty()) throw new IllegalArgumentException("Configure cloud.run.audiences before enabling IAM");
        this.fetcher = fetcher;
        this.clock = clock;
    }
    public synchronized String bearerFor(URI destination) throws IOException {
        String origin = destination.getScheme() + "://" + destination.getHost();
        if (destination.getUserInfo() != null || destination.getPort() != -1 || !audiences.contains(origin)) {
            throw new IOException("Refusing workload credentials for an unconfigured service origin");
        }
        long now = clock.instant().getEpochSecond();
        Cached cached = cache.get(origin);
        if (cached == null || cached.expires() <= now + 60) {
            try {
                String token = fetcher.fetch(origin);
                String[] parts = token.split("\\.");
                if (parts.length != 3) throw new IOException("Metadata server returned an invalid ID token");
                var claims = mapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
                long expires = claims.path("exp").asLong(0);
                if (!origin.equals(claims.path("aud").asText()) || expires <= now + 60) {
                    throw new IOException("Metadata token audience or lifetime is invalid");
                }
                cached = new Cached(token, expires);
                cache.put(origin, cached);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while acquiring workload identity", ex);
            } catch (IllegalArgumentException ex) {
                throw new IOException("Metadata server returned malformed token claims", ex);
            }
        }
        return "Bearer " + cached.token();
    }
    private static TokenFetcher adcFetcher() {
        return new TokenFetcher() {
            private GoogleCredentials credentials;
            @Override public String fetch(String audience) throws IOException {
                if (credentials == null) credentials = GoogleCredentials.getApplicationDefault();
                if (!(credentials instanceof IdTokenProvider provider)) {
                    throw new IOException("Runtime ADC does not support Google ID tokens");
                }
                return IdTokenCredentials.newBuilder().setIdTokenProvider(provider)
                    .setTargetAudience(audience).setOptions(java.util.List.of()).build().refreshAccessToken().getTokenValue();
            }
        };
    }
}
