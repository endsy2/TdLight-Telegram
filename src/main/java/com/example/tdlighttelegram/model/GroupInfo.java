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
public class GroupInfo {
    
    private Long id;
    private String title;
    private String username;
    private String description;
    private Integer memberCount;
    private Boolean isChannel;
    private Boolean isSupergroup;
    private Boolean isPublic;
    private String inviteLink;
    private String photoUrl;
    private LocalDateTime joinedDate;
    private String status; // JOINED, LEFT, BANNED, etc.
    private Boolean canSendMessages;
    private Boolean canSendMedia;
    private Boolean canInviteUsers;
    private LocalDateTime updatedAt;
}
