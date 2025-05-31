package com.example.cameraproject_2;

public class Invitation {
    private String invitationId;
    private String groupName;

    public Invitation(String invitationId, String groupName) {
        this.invitationId = invitationId;
        this.groupName = groupName;
    }

    public String getInvitationId() {
        return invitationId;
    }

    public String getGroupName() {
        return groupName;
    }
}