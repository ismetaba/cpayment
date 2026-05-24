package com.cpayment.custody.infra.cusserver.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AMQP defaults to {@code SimpleMessageConverter} (Java serialization), which
 * cannot decode the JSON payloads cus-server publishes. We replace it with a
 * Jackson-based converter and pin the {@link SimpleRabbitListenerContainerFactory} so
 * that {@code @RabbitListener} methods receive deserialized DTOs directly.
 *
 * <h2>Trusted packages</h2>
 * <p>The type mapper is configured to trust only DTOs under
 * {@code com.cpayment.custody.infra.cusserver.event.dto} — preventing arbitrary
 * deserialization gadgets if a message carries a {@code __TypeId__} header.
 */
@Configuration
public class CusServerRabbitConfig {

    private static final String TRUSTED_DTO_PACKAGE =
        "com.cpayment.custody.infra.cusserver.event.dto.*";

    @Bean
    public MessageConverter cusServerJsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages(TRUSTED_DTO_PACKAGE);
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            MessageConverter cusServerJsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(cusServerJsonMessageConverter);
        return factory;
    }
}
