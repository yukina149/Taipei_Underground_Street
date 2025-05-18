package com.example.cameraproject_2;

public class User {
    private String id;
    private String username;
    private boolean isSelected;

    public User(String id, String username) {
        this.id = id;
        this.username = username;
        this.isSelected = true; // 默認勾選
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }
}