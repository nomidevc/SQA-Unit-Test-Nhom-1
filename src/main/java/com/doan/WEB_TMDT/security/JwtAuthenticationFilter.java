package com.doan.WEB_TMDT.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String path = request.getServletPath();

        //  Chỉ bỏ qua các endpoint public (không cần JWT)
        if (path.equals("/api/auth/login") ||
            path.equals("/api/auth/register/send-otp") ||
            path.equals("/api/auth/register/verify-otp") ||
            path.equals("/api/auth/first-change-password") ||
            path.equals("/api/payment/ipn") ||
            path.startsWith("/api/payment/sepay/webhook") ||
            path.matches("/api/payment/[^/]+/status") ||
            path.equals("/api/employee-registration/apply") ||
            path.equals("/error")) {
            chain.doFilter(request, response);
            return;
        }
        
        // Cho phép GET public category endpoints (không cần JWT)
        if (path.startsWith("/api/categories") && request.getMethod().equals("GET")) {
            chain.doFilter(request, response);
            return;
        }
        
        // Cho phép GET public product endpoints (không cần JWT)
        if (path.startsWith("/api/products") && request.getMethod().equals("GET")) {
            // Nhưng không cho phép warehouse endpoints
            if (!path.startsWith("/api/products/warehouse")) {
                chain.doFilter(request, response);
                return;
            }
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        String email;
        try {
            email = jwtService.extractEmail(token);
        } catch (Exception e) {
            System.out.println("JWT Token invalid: " + e.getMessage());
            chain.doFilter(request, response);
            return;
        }

        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails;
            try {
                userDetails = userDetailsService.loadUserByUsername(email);
            } catch (UsernameNotFoundException e) {
                System.out.println("⚠️ User not found: " + email);
                chain.doFilter(request, response);
                return;
            }
            if (jwtService.isTokenValid(token, userDetails)) {

                Claims claims = jwtService.extractAllClaims(token);
                List<SimpleGrantedAuthority> authorities = new ArrayList<>();

                // Add all claims as authorities
                claims.forEach((key, value) -> {
                    if (key.equals("role") || key.startsWith("ROLE_") || key.equals("position") || 
                        key.equals("SALE") || key.equals("SALES") || key.equals("WAREHOUSE") || 
                        key.equals("PRODUCT_MANAGER") || key.equals("ACCOUNTANT") || key.equals("CSKH") ||
                        key.equals("ADMIN") || key.equals("EMPLOYEE") || key.equals("CUSTOMER")) {
                        authorities.add(new SimpleGrantedAuthority(key));
                        System.out.println("✅ Added authority: " + key);
                    }
                });

                Object role = claims.get("role");
                if (role != null) {
                    authorities.add(new SimpleGrantedAuthority(role.toString()));
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toString()));
                    System.out.println("✅ User role: " + role.toString());
                }

                Object position = claims.get("position");
                if (position != null) {
                    authorities.add(new SimpleGrantedAuthority(position.toString()));
                    // Add ROLE_EMPLOYEE for all employee positions
                    authorities.add(new SimpleGrantedAuthority("EMPLOYEE"));
                    authorities.add(new SimpleGrantedAuthority("ROLE_EMPLOYEE"));
                    System.out.println("✅ User position: " + position.toString());
                } else {
                    System.out.println("⚠️ No position found in JWT claims");
                }
                
                System.out.println("🔑 Final authorities: " + authorities);

                userDetails.getAuthorities().forEach(a -> {
                    if (authorities.stream().noneMatch(x -> x.getAuthority().equals(a.getAuthority()))) {
                        authorities.add(new SimpleGrantedAuthority(a.getAuthority()));
                    }
                });

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(userDetails, null, authorities);
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        chain.doFilter(request, response);
    }

}
