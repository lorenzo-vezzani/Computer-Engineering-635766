package it.unipi.lsmsd.utility;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

// Class to hash and verify the password
// Utility class so cannot be instantiated 
public final class PasswordHashUtil {
    
    // Single reusable instance of BCryptPasswordEncoder throughout the application's lifecycle
    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

     // Private constructor to prevent instantiation
     private PasswordHashUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // Hash the rawPassword with BCryptPasswordEncoder
    public static String hashPassword(String rawPassword) {
        // Check null or empty values 
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }

        try { return encoder.encode(rawPassword);} 
        catch (Exception e) { throw e;}
    }

    // Match and verify the rawPassword and hashedPassword with BCryptPasswordEncoder
    public static boolean verifyPassword(String rawPassword, String hashedPassword) {
        try { return encoder.matches(rawPassword, hashedPassword);}
        catch (IllegalArgumentException e) { throw e;}
    }
}