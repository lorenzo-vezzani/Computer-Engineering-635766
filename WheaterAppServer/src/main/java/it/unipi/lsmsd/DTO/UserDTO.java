package it.unipi.lsmsd.DTO;

import it.unipi.lsmsd.model.Role;

public class UserDTO {
    private String username;
    private String password;
    private String email;
    private Role role;
    private String adminCode;

    //Setters and Getters
    public String getUsername() { return username; }

    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password;}

    public void setPassword(String password) { this.password = password; }

    public String getEmail() { return email;}

    public void setEmail(String email) { this.email = email; }
    
    public Role getRole() { return role;}

    public void setRole(Role role) { this.role = role; }

    public String getAdminCode() { return adminCode;}

    public void setAdminCode(String adminCode) { this.adminCode = adminCode;}
}
