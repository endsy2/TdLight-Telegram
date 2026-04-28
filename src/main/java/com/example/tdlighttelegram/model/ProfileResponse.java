package com.example.tdlighttelegram.model;

import it.tdlight.jni.TdApi;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProfileResponse {
    private TdApi.ChatPhoto photo;
    private int totalCount;
}
