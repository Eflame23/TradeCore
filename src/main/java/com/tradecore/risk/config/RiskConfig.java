package com.tradecore.risk.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Spring configuration providing default beans for the Risk Management module.
 */
@Configuration
@EnableConfigurationProperties(RiskProperties.class)
public class RiskConfig {

    @Bean
    public Clock riskClock() {
        return Clock.systemUTC();
    }
}
