package com.srinu.otelpoc.gateway.filter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.assertThat;
class AuthRouteConfigurationTest {
    @Configuration @EnableConfigurationProperties(GatewayProperties.class) static class Routes {}
    @Test void deployedCanonicalAuthOriginBindsToRealLoginRoute() {
        var yaml=new YamlPropertiesFactoryBean();yaml.setResources(new ClassPathResource("application.yml"));
        new ApplicationContextRunner().withUserConfiguration(Routes.class)
            .withInitializer(app->app.getEnvironment().getPropertySources().addLast(new PropertiesPropertySource("applicationYaml",yaml.getObject())))
            .withPropertyValues("SERVICES_AUTH_URL=https://auth-service-fddyka6g2a-uc.a.run.app")
            .run(app->{
                var auth=app.getBean(GatewayProperties.class).getRoutes().stream().filter(route->route.getId().equals("auth-service")).findFirst().orElseThrow();
                assertThat(auth.getUri().toString()).isEqualTo("https://auth-service-fddyka6g2a-uc.a.run.app");
                assertThat(auth.getPredicates().getFirst().getArgs().values()).contains("/api/v1/auth/**");
            });
    }
}
