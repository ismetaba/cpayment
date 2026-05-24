package com.cpayment;

import com.cpayment.custody.infra.cusserver.config.CusServerAutoConfiguration;
import com.cpayment.payment.infra.config.PaymentAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({CusServerAutoConfiguration.class, PaymentAutoConfiguration.class, SecurityConfiguration.class})
public class CpaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(CpaymentApplication.class, args);
    }
}
