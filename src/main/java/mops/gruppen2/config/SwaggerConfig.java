package mops.gruppen2.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import java.util.Collections;

@Profile("dev")
@Configuration
@EnableSwagger2
public class SwaggerConfig {

    @Bean
    public Docket productAPI() {
        return new Docket(DocumentationType.SWAGGER_2)
                .select()
                .paths(PathSelectors.ant("/gruppen2/api/**"))
                .apis(RequestHandlerSelectors.basePackage("mops.gruppen2"))
                .build()
                .apiInfo(apiMetadata());
    }

    private ApiInfo apiMetadata() {
        return new ApiInfo(
                "Gruppenbildung API",
                "API zum anfragen/aktualisieren der Gruppendaten.",
                "0.0.1",
                "Free to use",
                new Contact("gruppen2", "https://github.com/hhu-propra2/abschlussprojekt-it-bois", ""),
                "",
                "",
                Collections.emptyList()
        );
    }
}
