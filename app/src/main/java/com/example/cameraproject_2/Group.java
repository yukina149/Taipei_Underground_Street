package com.example.cameraproject_2;

import java.util.List;

public class Group {
    private String groupName;
    private String creatorId;
    private String creatorAvatarUrl;
    private List<String> members;
    private String lastMessage;
    private String lastMessageTime;

    public Group(String groupName, String creatorId, String creatorAvatarUrl, List<String> members) {
        this.groupName = groupName;
        this.creatorId = creatorId;
        this.creatorAvatarUrl = creatorAvatarUrl;
        this.members = members;
        this.lastMessage = "無訊息"; // Default value
        this.lastMessageTime = ""; // Default value
    }

    public String getGroupName() {
        return groupName;
    }

    public String getCreatorId() {
        return creatorId;
    }

    public String getCreatorAvatarUrl() {
        return creatorAvatarUrl != null ? creatorAvatarUrl : "";
    }

    public List<String> getMembers() {
        return members;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public String getLastMessageTime() {
        return lastMessageTime;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public void setLastMessageTime(String lastMessageTime) {
        this.lastMessageTime = lastMessageTime;
    }
}