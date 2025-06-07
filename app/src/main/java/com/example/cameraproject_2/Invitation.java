package com.example.cameraproject_2;

public class Invitation {
    private String invitationId;
    private String groupName;
    private String status;

    public Invitation(String invitationId, String groupName) {
        this.invitationId = invitationId;
        this.groupName = groupName;
        this.status = status;
    }
    public String getStatus() {
        return status;
    }

    public String getInvitationId() {
        return invitationId;
    }

    public String getGroupName() {
        return groupName;
    }
}