package com.quyen.geekticket.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI geekTicketOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("GeekTicket API")
                        .description("Concert ticket booking backend API")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Phạm Anh Quyền")
                                .email("phamanhquyen.work@gmail.com")));
    }
}
