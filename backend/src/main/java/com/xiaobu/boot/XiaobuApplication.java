package com.xiaobu.boot;

import com.aegisid.foundation.data.id.EntityIdGenerator;
import com.aegisid.foundation.data.id.UuidV7EntityIdGenerator;
import com.aegisid.foundation.web.error.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.xiaobu")
@EntityScan("com.xiaobu.ledger.infrastructure")
@EnableJpaRepositories("com.xiaobu.ledger.infrastructure")
@Import(GlobalExceptionHandler.class)
public class XiaobuApplication {

  @Bean
  EntityIdGenerator entityIdGenerator() {
    return new UuidV7EntityIdGenerator();
  }

  public static void main(String[] args) {
    SpringApplication.run(XiaobuApplication.class, args);
  }
}
