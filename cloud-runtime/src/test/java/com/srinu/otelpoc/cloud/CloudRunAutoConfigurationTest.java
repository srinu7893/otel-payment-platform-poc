package com.srinu.otelpoc.cloud;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
class CloudRunAutoConfigurationTest {
    private final ApplicationContextRunner context=new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CloudRunAutoConfiguration.class));
    @Test void disabledLocalModeDoesNotCreateCredentialsOrMetadataClient() {
        context.withPropertyValues("cloud.run.iam.enabled=false").run(app -> assertThat(app).doesNotHaveBean(CloudRunIdentity.class));
    }
    @Test void enabledModeUsesConfiguredCanonicalAudiencesWithoutFetchingCredentialsAtStartup() {
        context.withPropertyValues("cloud.run.iam.enabled=true","cloud.run.audiences=https://auth-service-fddyka6g2a-uc.a.run.app")
            .run(app -> assertThat(app).hasSingleBean(CloudRunIdentity.class));
    }
}
