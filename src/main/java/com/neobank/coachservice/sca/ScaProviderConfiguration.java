package com.neobank.coachservice.sca;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;

@Configuration
public class ScaProviderConfiguration {

    @Bean
    @ConditionalOnProperty(name = "services.sca.enabled", havingValue = "true")
    ScaProvider httpScaProvider(RestClient.Builder restClientBuilder, Environment environment) {
        return new HttpScaProvider(restClientBuilder, environment);
    }

    @Bean
    @ConditionalOnProperty(name = "services.sca.enabled", havingValue = "false", matchIfMissing = true)
    ScaProvider disabledScaProvider() {
        return new DisabledScaProvider();
    }
}
