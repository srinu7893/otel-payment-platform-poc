package com.srinu.otelpoc.cloud;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(name = "cloud.run.iam.enabled", havingValue = "true")
public class CloudRunAutoConfiguration {
    @Bean
    CloudRunIdentity cloudRunIdentity(@Value("${cloud.run.audiences}") String origins) {
        return new CloudRunIdentity(Arrays.stream(origins.split(",")).map(String::strip)
            .filter(value -> !value.isEmpty()).collect(Collectors.toSet()));
    }
    @Bean
    RestClientCustomizer cloudRunRestClientCustomizer(CloudRunIdentity identity) {
        return builder -> builder.requestInterceptor((request, body, execution) -> {
            request.getHeaders().set(CloudRunIdentity.HEADER, identity.bearerFor(request.getURI()));
            return execution.execute(request, body);
        });
    }
}
