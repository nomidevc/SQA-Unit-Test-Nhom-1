package com.doan.WEB_TMDT.config;

import com.doan.WEB_TMDT.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/payment/sepay/webhook").permitAll() // SePay webhook
                        .requestMatchers("/api/payment/test-webhook/**").permitAll() // Test webhook (dev only)
                        .requestMatchers("/api/payment/{paymentCode}/status").permitAll() // Check status
                        .requestMatchers("/api/webhooks/**").permitAll() // GHN webhook
                        .requestMatchers("/api/employee-registration/apply").permitAll()
                        .requestMatchers("/api/test/**").permitAll()
                        
                        // Public product & category endpoints (for all users)
                        .requestMatchers("/api/categories", "/api/categories/tree", "/api/categories/active").permitAll()
                        .requestMatchers("/api/categories/{id}").permitAll()
                        .requestMatchers("/api/products").permitAll()
                        .requestMatchers("/api/products/{id}").permitAll()
                        .requestMatchers("/api/products/{id}/with-specs").permitAll()
                        .requestMatchers("/api/products/{id}/images").permitAll() // Product images - public
                        .requestMatchers("/api/products/search-by-specs").permitAll()
                        .requestMatchers("/api/products/filter-by-specs").permitAll()
                        .requestMatchers("/api/product/**").permitAll()
                        
                        // Public review endpoints (anyone can view reviews)
                        .requestMatchers("/api/reviews/product/**").permitAll()
                        
                        // Public shipping endpoints (for calculating shipping fee and address selection)
                        .requestMatchers("/api/shipping/calculate-fee").permitAll()
                        .requestMatchers("/api/shipping/provinces").permitAll()
                        .requestMatchers("/api/shipping/districts/**").permitAll()
                        .requestMatchers("/api/shipping/wards/**").permitAll()
                        
                        // Customer endpoints (Cart, Orders, Profile)
                        .requestMatchers("/api/cart/**").hasAnyAuthority("CUSTOMER", "ADMIN")
                        .requestMatchers("/api/customer/all").hasAnyAuthority("ADMIN", "EMPLOYEE", "SALE", "SALES", "SHIPPER", "WAREHOUSE", "PRODUCT_MANAGER", "ACCOUNTANT", "CSKH")
                        .requestMatchers("/api/customer/**").hasAnyAuthority("CUSTOMER", "ADMIN")
                        .requestMatchers("/api/orders/customer/**").hasAnyAuthority("ADMIN", "EMPLOYEE", "SALE", "SALES", "SHIPPER", "WAREHOUSE", "PRODUCT_MANAGER", "ACCOUNTANT", "CSKH")
                        .requestMatchers("/api/orders/**").hasAnyAuthority("CUSTOMER", "ADMIN", "EMPLOYEE", "SALE", "SALES", "SHIPPER", "WAREHOUSE", "PRODUCT_MANAGER", "ACCOUNTANT", "CSKH")
                        
                        // Warehouse endpoints (Inventory management)
                        // Note: /api/inventory/stock cho phép PRODUCT_MANAGER xem (read-only)
                        .requestMatchers("/api/inventory/stock").hasAnyAuthority("WAREHOUSE", "PRODUCT_MANAGER", "ADMIN", "EMPLOYEE", "SALE", "ACCOUNTANT", "CSKH", "SHIPPER")
                        .requestMatchers("/api/inventory/suppliers").hasAnyAuthority("WAREHOUSE", "PRODUCT_MANAGER", "ADMIN", "EMPLOYEE", "SALE", "ACCOUNTANT", "CSKH", "SHIPPER")
                        .requestMatchers("/api/inventory/export-orders/**").hasAnyAuthority("WAREHOUSE", "ADMIN", "EMPLOYEE", "SALE", "SALES", "PRODUCT_MANAGER", "ACCOUNTANT", "CSKH", "SHIPPER")
                        .requestMatchers("/api/inventory/purchase-orders/**").hasAnyAuthority("WAREHOUSE", "ADMIN", "EMPLOYEE", "SALE", "SALES", "PRODUCT_MANAGER", "ACCOUNTANT", "CSKH", "SHIPPER")
                        .requestMatchers("/api/inventory/**").hasAnyAuthority("WAREHOUSE", "ADMIN")
                        
                        // Product Manager endpoints (Product & Category management)
                        .requestMatchers("/api/products/warehouse/**").hasAnyAuthority("PRODUCT_MANAGER", "ADMIN")
                        .requestMatchers("/api/products/publish").hasAnyAuthority("PRODUCT_MANAGER", "ADMIN")
                        .requestMatchers("/api/products/*/images").hasAnyAuthority("PRODUCT_MANAGER", "ADMIN") // Add/manage images
                        .requestMatchers("/api/products/*/images/*/primary").hasAnyAuthority("PRODUCT_MANAGER", "ADMIN") // Set primary
                        .requestMatchers("/api/products/images/*").hasAnyAuthority("PRODUCT_MANAGER", "ADMIN") // Delete image
                        .requestMatchers("/api/products/*/images/reorder").hasAnyAuthority("PRODUCT_MANAGER", "ADMIN") // Reorder
                        
                        // Admin order management (ADMIN + SALES_STAFF + SHIPPER)
                        .requestMatchers("/api/admin/orders/**").hasAnyAuthority("ADMIN", "SALE", "SALES", "EMPLOYEE", "SHIPPER")
                        
                        // Dashboard endpoints (ADMIN + All Employee Positions)
                        .requestMatchers("/api/dashboard/**").hasAnyAuthority("ADMIN", "EMPLOYEE", "ACCOUNTANT", "SALE", "SALES", "WAREHOUSE", "PRODUCT_MANAGER", "CSKH", "SHIPPER")
                        
                        // Accounting endpoints (ADMIN + ACCOUNTANT)
                        .requestMatchers("/api/accounting/**").hasAnyAuthority("ADMIN", "ACCOUNTANT")
                        
                        // Admin only endpoints
                        .requestMatchers("/api/employee-registration/list").hasAuthority("ADMIN")
                        .requestMatchers("/api/employee-registration/pending").hasAuthority("ADMIN")
                        .requestMatchers("/api/employee-registration/approve/**").hasAuthority("ADMIN")
                        .requestMatchers("/api/admin/**").hasAuthority("ADMIN")
                        
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring().requestMatchers(
                "/v3/api-docs/**",
                "/swagger-ui/**",
                "/swagger-ui.html",
                "/swagger-resources/**",
                "/webjars/**"
        );
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Cho phép tất cả origins (bao gồm localhost, ngrok, production)
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        
        // Hoặc cụ thể hơn (uncomment nếu muốn giới hạn):
        // configuration.setAllowedOrigins(Arrays.asList(
        //     "http://localhost:3000",
        //     "https://*.ngrok-free.app",
        //     "https://*.ngrok.io"
        // ));
        
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        
        // Webhook configuration - allow all origins for webhook endpoints
        CorsConfiguration webhookConfig = new CorsConfiguration();
        webhookConfig.setAllowedOriginPatterns(Arrays.asList("*")); // Allow all origins for webhooks
        webhookConfig.setAllowedMethods(Arrays.asList("POST", "GET", "OPTIONS"));
        webhookConfig.setAllowedHeaders(Arrays.asList("*"));
        webhookConfig.setAllowCredentials(false); // No credentials needed for webhooks
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/payment/sepay/webhook", webhookConfig);
        source.registerCorsConfiguration("/api/payment/test-webhook/**", webhookConfig);
        source.registerCorsConfiguration("/api/webhooks/**", webhookConfig); // GHN webhook
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
