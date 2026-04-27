package com.example.tdlighttelegram.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupMemberInfo {
    
    /**
     */
    private Long userId;
    
    /**
     */
    private Long groupId;
    
    /**
     */
    private String firstName;
    
    /**
     */
    private String lastName;
    
    /**
     */
    private String username;
    
    /**
     */
    private String phoneNumber;
    
    /**
     */
    private String bio;
    
    /**
     */
    private Boolean isBot;
    
    /**
     */
    private Boolean isVerified;
    
    /**
     */
    private Boolean isPremium;
    
    /**
     */
    private Boolean isDeleted;
    
    /**
     */
    private String photoUrl;
    
    /**
     */
    private String memberStatus;
    
    /**
     */
    private String onlineStatus;
    
    /**
     */
    private LocalDateTime lastOnlineTime;
    
    /**
     */
    private LocalDateTime joinedGroupTime;
    
    /**
     */
    private Boolean canSendMessages;
    
    /**
     */
    private Boolean canSendMedia;
    
    /**
     */
    private Boolean canInviteUsers;
    
    /**
     */
    private Boolean canChangeInfo;
    
    /**
     */
    private Boolean canPinMessages;
    
    /**
     */
    private Boolean canDeleteMessages;
    
    /**
     */
    private Boolean canBanUsers;
    
    /**
     */
    private Boolean canRestrictMembers;
    
    /**
     */
    private Boolean canPromoteMembers;
    
    /**
     */
    private String customTitle;
    
    /**
     */
    private LocalDateTime updatedAt;
    
    /**
     */
    public String getDisplayName() {
        if (username != null && !username.isEmpty()) {
            return "@" + username;
        }
        
        StringBuilder name = new StringBuilder();
        if (firstName != null && !firstName.isEmpty()) {
            name.append(firstName);
        }
        if (lastName != null && !lastName.isEmpty()) {
            if (name.length() > 0) {
                name.append(" ");
            }
            name.append(lastName);
        }
        
        return name.length() > 0 ? name.toString() : "User " + userId;
    }
    
    /**
     */
    public boolean isAdmin() {
        return "CREATOR".equals(memberStatus) || "ADMINISTRATOR".equals(memberStatus);
    }
    
    /**
     */
    public boolean isActiveUser() {
        return "ONLINE".equals(onlineStatus) || 
               "RECENTLY".equals(onlineStatus) || 
               "LAST_WEEK".equals(onlineStatus);
    }
    
    /**
     */
    public boolean isValidUser() {
        return !Boolean.TRUE.equals(isDeleted) && 
               !Boolean.TRUE.equals(isBot) &&
               !"LONG_TIME_AGO".equals(onlineStatus);
    }
}
