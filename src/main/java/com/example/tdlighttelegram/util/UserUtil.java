package com.example.tdlighttelegram.util;

import it.tdlight.jni.TdApi;
import org.springframework.stereotype.Component;

@Component
public class UserUtil {
    /**
     */
    public String buildUserDisplayName(TdApi.User user) {
        StringBuilder name = new StringBuilder();
        if (user.firstName != null && !user.firstName.isEmpty()) {
            name.append(user.firstName);
        }
        if (user.lastName != null && !user.lastName.isEmpty()) {
            if (name.length() > 0) {
                name.append(" ");
            }
            name.append(user.lastName);
        }
        return name.length() > 0 ? name.toString() : "User " + user.id;
    }

}
