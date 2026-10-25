package com.campus.lostfound.model;

public class User {
    private final String id;
    private final String name;
    private final String email;
    private final String passwordHash;
    private final String role;

    public User(String id, String name, String email, String passwordHash, String role) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getRole() { return role; }
    public boolean isAdmin() { return "ADMIN".equals(role); }
}
