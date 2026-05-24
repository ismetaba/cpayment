package com.cpayment.custody.infra.cusserver.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Single entry point that wires the cus-server custody adapter.
 * The bootable dist module just needs to @Import or component-scan this package.
 */
@Configuration
@EnableConfigurationProperties(CusServerProperties.class)
@ComponentScan(basePackages = "com.cpayment.custody.infra.cusserver")
public class CusServerAutoConfiguration {
}
