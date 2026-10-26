package it.unipi.lsmsd.controller;

import it.unipi.lsmsd.DTO.UserDTO;
import it.unipi.lsmsd.exception.EmailFormatException;
import it.unipi.lsmsd.exception.UnauthorizedException;
import it.unipi.lsmsd.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.dao.DuplicateKeyException;

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody UserDTO userDTO){
        try {
             // returns token on successful login
            String token = userService.login(userDTO); 
            return ResponseEntity
                .status(HttpStatus.OK)
                .body(String.format("Logged in successfully, JWTToken: %s", token));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body("Invalid username or password.");
        } catch (Exception ex) {
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("An unexpected error occurred: " + ex.getMessage());
        }
           
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(@RequestHeader("Authorization") String token) {
        try{
            userService.logout(token);
            // Successful Logout
            return ResponseEntity
                .status(HttpStatus.OK)
                .body("Logged out successfully");

        }catch(Exception ex){
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("An unexpected error occurred: " + ex.getMessage());
        }
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody UserDTO userDTO) {
        try {
            userService.register(userDTO);
            // User created
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(String.format("User %s created successfully.", userDTO.getUsername()) + "\n");
        } catch (DuplicateKeyException ex) {
            // Duplicate Key Error
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body("Error Conflict: " +  ex.getMessage() + "\n");
        } catch (EmailFormatException ex) {
            // Email not correctly formatted
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("Error Bad-request: " + ex.getMessage() + "\n");
        }
        catch (UnauthorizedException Ue){
            return ResponseEntity
                    .status(HttpStatus.NOT_ACCEPTABLE)
                    .body(Ue.getMessage() + "\n");
        }
        catch (Exception ex) {
            // Other unexpected errors
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An unexpected error occurred: " + ex.getMessage() + "\n");
        }
    }
}