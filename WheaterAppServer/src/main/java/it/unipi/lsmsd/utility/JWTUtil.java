package it.unipi.lsmsd.utility;

import it.unipi.lsmsd.model.User;
import it.unipi.lsmsd.model.Role;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import java.security.Key;
import java.util.Date;

// Generates and Parse JWT tokens for user authentication
// Utility class so cannot be instantiated
public final class JWTUtil {
    // TODO: Create the secret key and use environment variables or a secure method to store the key
    // Single constant instance of cryptographic key suitable for HMAC-SHA algorithms (HS256 here)
    private static final Key SECRET_KEY = Keys.hmacShaKeyFor("yourSecretKeyMustBeAtLeast256BitsLong!".getBytes());

    // Private constructor to prevent instantiation
    private JWTUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // Generates JWT token with username and role only
    // EX: String token = JwtUtil.generateToken("adminUser", "ADMIN");
    public static String generateToken(String username, Role role) {
        try {
            return Jwts.builder()
                .setSubject(username) // Username as subject
                .claim("role", role.name()) // Add role as a custom claim
                .setIssuedAt(new Date()) // Token issue time
                .signWith(SECRET_KEY, SignatureAlgorithm.HS256) // Signs the JWT with the SECRET_KEY 
                .compact(); // Finalizes and returns the JWT as a compact, URL-safe String
        } catch (JwtException | IllegalArgumentException e) { throw e;}
    }

    // Parses JWT token and returns the User with only username and role
    // TODO: return UserDTO recommended instead of Model Class User
    public static User extractUser(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(SECRET_KEY) // Set the key used to verify the token’s signature.
                .build() //Builds the parser instance
                .parseClaimsJws(token) //Parse the token and verify the signature
                .getBody(); // the claimsfrom the token

            // Extract the username and role from the token
            String username = claims.getSubject();
            Role role = Role.valueOf(claims.get("role", String.class));

            // Return reduced User
            return new User(username, role); 

        } catch (ExpiredJwtException e) { 
            // Token expired
            throw e;

        } catch (JwtException e) {
            // Invalid token
            throw e;
        }        
    }

    // Extract claims from JWT token
    public static Claims extractClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(SECRET_KEY)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

}