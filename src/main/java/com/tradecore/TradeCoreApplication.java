package com.tradecore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.tradecore", "com.trading"})
public class TradeCoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(TradeCoreApplication.class, args);
    }
}
