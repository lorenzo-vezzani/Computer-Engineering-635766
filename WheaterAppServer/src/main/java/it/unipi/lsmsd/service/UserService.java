package it.unipi.lsmsd.service;

import it.unipi.lsmsd.DTO.UserDTO;
import it.unipi.lsmsd.exception.EmailFormatException;
import it.unipi.lsmsd.exception.UnauthorizedException;
import it.unipi.lsmsd.model.Role;
import it.unipi.lsmsd.model.User;
import it.unipi.lsmsd.repository.UserRepository;
import it.unipi.lsmsd.utility.JWTUtil;
import it.unipi.lsmsd.utility.PasswordHashUtil;
import redis.clients.jedis.exceptions.JedisConnectionException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class UserService {
    
    // Dependency Injection (using @Autowired)
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RedisSessionService sessionRedisService;

    private static final String ADMIN_SECRET_CODE = "ABC123SECRET";

    private void checkRole(User user, Role requiredRole) {
        if (user.getRole().ordinal() < requiredRole.ordinal()) {
            throw new UnauthorizedException("Required role: " + requiredRole);
        }
    }

    // Helper method to retrieve the User object using the provided token
    public User getUserFromToken(String token) throws RuntimeException {
        // Extract the reduced user (just username and role) from the token
        User reducedUser = JWTUtil.extractUser(token);

        // Fetch the complete user from the database using the username
        Optional<User> userOpt = userRepository.findByUsername(reducedUser.getUsername());

        // If the user is not found in the database, throw an exception
        if (userOpt.isEmpty()) {
            throw new RuntimeException("User not found");
        }

        // Return the complete User object if found
        return userOpt.get();
    }

    public User getAndCheckUserFromToken(String token, Role requiredRole) throws RuntimeException {
        User user = getUserFromToken(token);
        checkRole(user, requiredRole);
        return user;
    }

    // Login 
    public String login(UserDTO userDTO) throws Exception{      
        try {

            // Get the User from MondoDB via Repository with username
            Optional<User> userOpt = userRepository.findByUsername(userDTO.getUsername());
            
            // Validate the User is found with given username and then the password matches (DTO vs Model)
            if (userOpt.isEmpty() || !PasswordHashUtil.verifyPassword(userDTO.getPassword(), userOpt.get().getPassword())) {
                throw new Exception("Invalid credentials");
            }
                    
            // Generate token
            String token = JWTUtil.generateToken(userDTO.getUsername(), userOpt.get().getRole());

            // Save the session in Redis
            sessionRedisService.saveSession(token, userDTO.getUsername());
            return token;
        } catch (JedisConnectionException e) {
            throw new JedisConnectionException("Reddis Server Error: " + e.getMessage(), e);
        } catch (Exception ex) {
            throw ex;
        }  
    }

    // Logout
    public void logout(String token) throws Exception{
        try {
            // delete the session
            sessionRedisService.deleteSession(token);
        } catch (Exception ex) {
            throw ex;
        }
        
    }

    // Register User 
    public void register(UserDTO userDTO) throws Exception{
        try {
            // Validate user data (example: email validation)
            if (!isValidEmail(userDTO.getEmail())) {
                throw new EmailFormatException(userDTO.getEmail() + " is not a valid email format");
            }

            // Hash password
            String hashedPassword = PasswordHashUtil.hashPassword(userDTO.getPassword());

            // Requested role or default
            Role requestedRole = userDTO.getRole() != null ? userDTO.getRole() : Role.REGISTERED_USER;

            // Create User model from UserDTO
            User user = new User(
                    userDTO.getUsername(),
                    hashedPassword,
                    userDTO.getEmail(),
                    requestedRole
            );

            if (requestedRole == Role.ADMIN) {
                if (userDTO.getAdminCode() == null || !ADMIN_SECRET_CODE.equals(userDTO.getAdminCode())) {
                    throw new UnauthorizedException("Invalid admin authorization code");
                }
            }

            // Save the user in the database
            userRepository.save(user);
        } catch (Exception ex) {
            throw ex;
        }
    }

    // Validate email format using regex
    private boolean isValidEmail(String email) {
        String emailRegex = "^[A-Za-z0-9+_.-]+@(.+)$";
        Pattern pattern = Pattern.compile(emailRegex);
        Matcher matcher = pattern.matcher(email);
        return matcher.matches();
    }
}