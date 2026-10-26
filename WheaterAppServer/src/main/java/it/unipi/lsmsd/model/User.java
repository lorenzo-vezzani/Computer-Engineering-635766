package it.unipi.lsmsd.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.ArrayList;

@Document(collection = "users")
public class User {

    // Attributes
    @Id
    private String id;
    @Indexed(unique = true)
    private String username;
    private String password;
    @Indexed(unique = true)
    private String email;
    private List<String> listCityId = new ArrayList<>();
    private Role role; // Enum
    
    // Constructors
    public User() {}

    // Contructor required for JWT token parsing during Authentication
    public User(String username, Role role) {
        this.username = username;
        this.role = role;
    }

    // Constructor to create complete User instance
    public User(String username, String password, String email, Role role) {
        this.username = username;
        this.password = password;
        this.email = email;
        this.role = role;
    }

    //Getters and setters
    public String getId() { return id; }

    public void setId(String id) { this.id = id; }

    public String getUsername() { return username; }

    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }

    public void setPassword(String password) { this.password = password; }

    public String getEmail() { return email; }

    public void setEmail(String email) { this.email = email; }

    public Role getRole() { return role; }

    public List<String> getListCityId() { return listCityId; }

    public void setListCity(List<String> listCity) { this.listCityId = listCity; }
    
}

