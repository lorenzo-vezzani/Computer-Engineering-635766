package it.unipi.lsmsd.security;

import io.jsonwebtoken.Claims;
import it.unipi.lsmsd.model.Role;
import it.unipi.lsmsd.model.User;
import it.unipi.lsmsd.service.RedisSessionService;
import it.unipi.lsmsd.utility.JWTUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Component  // This annotation marks the class as a Spring bean
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private RedisSessionService sessionRedisService;

    // This method is the core of the filter. It runs once for each request to validate JWT.
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException 
    {

        // Extract the JWT token from the request's Authorization header
        String token = extractTokenFromRequest(request);
        // Check if the token and username are both not null --> Hence Valid !!
        if (token != null) {
            // Get the username from the session in Redis to validate the token
            String username = sessionRedisService.getUsernameFromSession(token);
            if(username != null){
                // Extract claims from the token (like user information and role)
                Claims claims = JWTUtil.extractClaims(token);
                Role role = Role.valueOf(claims.get("role", String.class));  // Extract the role from claims

                // Create a list of authorities (roles) which tell Spring Security what roles 
                // the authenticated user has, and thus what parts of the application the user is allowed to access.
                // IMPORTANT NOTE:  Role name "ADMIN" and  "REGISTERED_USER" is mapped to ROLE_ADMIN and ROLE_REGISTERED_USER internally
                List<GrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role.name()));
                // Create an authentication object that Spring Security can use with roles/authorities
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        new User(username, role), null, authorities);
                // Store the authentication object in the SecurityContext, which Spring Security uses
                // for accessing authentication information globally throughout the application -> Passes the control to 
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }            
        }
        // NOTE Else: Since the token is null which is aslo true for the public endpoints, simply continue to the filter chain
        // Continue the filter chain, allowing the request to move to the next filter or controller --> eventually moves to ...
        chain.doFilter(request, response);
    }

    // This helper method extracts the JWT token from the "Authorization" header
    private String extractTokenFromRequest(HttpServletRequest request) {
        // Get the Authorization header from the request
        String header = request.getHeader("Authorization");

        // Check if the header is not null and starts with "Bearer ", which indicates a JWT token
        if (header != null && header.startsWith("Bearer ")) {
            // Extract the token part after "Bearer "
            return header.substring(7);  // Remove the "Bearer " prefix
        }
        // If no token is found, return null
        return null;
    }
}