package com.raktsetu.server;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The title, description and sign-in scheme shown on the API documentation page ({@code /api-docs}). */
@Configuration
public class ApiDocsConfig {

    static final String SESSION = "session";

    @Bean
    OpenAPI raktSetuApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("RaktSetu API")
                        .version("1.0")
                        .description("""
                                Expiry-aware blood redistribution network. Sign in with POST /api/auth/login; the \
                                session cookie then authorises every call. Changes also need the X-XSRF-TOKEN header \
                                copied from the XSRF-TOKEN cookie (this page does that for you). Errors are JSON: \
                                {"error": ..., "message": ...} with 400 bad input, 401 not signed in, 403 not allowed, \
                                404 unknown facility, 409 refused (for example not enough stock), 429 too many \
                                failed sign-ins."""))
                .components(new Components().addSecuritySchemes(SESSION, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE).name("JSESSIONID")))
                .addSecurityItem(new SecurityRequirement().addList(SESSION));
    }
}
