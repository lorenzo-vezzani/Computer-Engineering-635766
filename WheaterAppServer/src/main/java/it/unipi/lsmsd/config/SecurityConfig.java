package it.unipi.lsmsd.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import it.unipi.lsmsd.security.JwtAuthenticationFilter;

// Configure Spring Security to handle JWT authentication and role-based authorization
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    // Initialize the custom JwtAuthenticationFilter to be configured to run before the AuthenticationFilter
    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // Disables CSRF for stateless JWT authentication
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/user/register",
                    "/user/login",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/*/**",
                    "/*/*/**",
                    "/swagger-ui.html").permitAll()  // Public endpoints
                .requestMatchers(HttpMethod.POST, "/hourly/**").hasRole("ADMIN") // Role name "ADMIN" is mapped to ROLE_ADMIN internally
                .anyRequest().authenticated() // Secure all other requests
            )
            // Register the custom JWT filter before Spring's default UsernamePasswordAuthenticationFilter
            // so that JWT tokens are processed first and SecurityContext is set appropriately
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();  // Build the security filter chain
    }

    //Configures the AuthenticationManager for user authentication. 
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

}
