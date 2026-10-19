package com.srinu.otelpoc.auth.config;
import org.springframework.context.annotation.*;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import java.time.Duration;
@Configuration public class RestClientConfig {
    @Bean RestClientCustomizer registrationTimeouts(@org.springframework.beans.factory.annotation.Value("${clients.http.read-timeout:25s}") Duration readTimeout){return builder->{
        var factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(Duration.ofSeconds(3));factory.setReadTimeout(readTimeout);builder.requestFactory(factory);
    };}
}
