package br.com.verx.cashflow.transaction.adapters.in.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean
    @ConditionalOnProperty(name = "cashflow.security.enabled", havingValue = "true")
    JwtDecoder jwtDecoder(@Value("${cashflow.security.jwt-issuer-uri}") String issuerUri) {
        if (issuerUri == null || issuerUri.isBlank()) {
            throw new IllegalStateException("JWT_ISSUER_URI é obrigatório quando SECURITY_ENABLED=true");
        }
        return JwtDecoders.fromIssuerLocation(issuerUri);
    }

    @Bean
    @ConditionalOnProperty(name = "cashflow.security.enabled", havingValue = "true")
    SecurityFilterChain securedFilterChain(HttpSecurity http,
                                           @Value("${cashflow.security.jwt-issuer-uri}") String issuerUri)
            throws Exception {
        if (issuerUri == null || issuerUri.isBlank()) {
            throw new IllegalStateException("JWT_ISSUER_URI é obrigatório quando SECURITY_ENABLED=true");
        }
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/transactions").hasAuthority("SCOPE_transactions:write")
                        .requestMatchers(HttpMethod.POST, "/v1/transactions/*/reversals").hasAuthority("SCOPE_transactions:write")
                        .requestMatchers(HttpMethod.GET, "/v1/transactions/**").hasAuthority("SCOPE_transactions:read")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    @ConditionalOnProperty(name = "cashflow.security.enabled", havingValue = "false", matchIfMissing = true)
    SecurityFilterChain localFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .build();
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("sub");
        return converter;
    }
}
