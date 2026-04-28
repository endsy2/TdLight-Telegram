package com.example.tdlighttelegram.service;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallService {
    private SimpleTelegramClient client;

    public CompletableFuture<TdApi.GroupCallStreams> getGroupStreams(int groupCallId) {

        TdApi.GetGroupCallStreams request = new TdApi.GetGroupCallStreams();
        request.groupCallId = groupCallId;

        return client.send(request).thenApply(result -> {
            if (result instanceof TdApi.GroupCallStreams streams) {

                log.info("Streams size: {}", streams.streams.length);

                for (TdApi.GroupCallStream s : streams.streams) {
                    log.info("Stream: {}", s);
                }

                return streams;
            }

            throw new RuntimeException("Failed to get group streams");
        });
    }
}
