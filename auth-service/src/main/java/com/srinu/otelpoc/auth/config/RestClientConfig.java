package com.srinu.otelpoc.auth.config;
import org.springframework.context.annotation.*;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import java.time.Duration;
@Configuration public class RestClientConfig {
    @Bean RestClientCustomizer registrationTimeouts(){return builder->{
        var factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(Duration.ofSeconds(3));factory.setReadTimeout(Duration.ofSeconds(25));builder.requestFactory(factory);
    };}
}
