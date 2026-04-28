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
public class UserInfo {
    
    private Long id;
    private String firstName;
    private String lastName;
    private String username;
    private String phoneNumber;
    private String bio;
    private Boolean isBot;
    private Boolean isVerified;
    private Boolean isPremium;
    private String languageCode;
    private String photoUrl;
    private String status; // ONLINE, OFFLINE, RECENTLY, etc.
    private LocalDateTime lastOnline;
    private Long groupId;
    private String groupTitle;
    private String memberStatus; // CREATOR, ADMINISTRATOR, MEMBER, etc.
    private LocalDateTime joinedGroupDate;
    private LocalDateTime updatedAt;
}
