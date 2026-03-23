package com.codecomp.codecomp.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.codecomp.codecomp.dto.RoomStateResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class RedisSubscriber implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {

        try {
            String json = new String(message.getBody());

            System.out.println("Received JSON from Redis: " + json);

            RoomStateResponse state = objectMapper.readValue(json, RoomStateResponse.class);

            String channel = new String(message.getChannel());
            System.out.println("Channel: " + channel);

            String roomIdStr = channel.split(":")[1];
            Long roomId = Long.parseLong(roomIdStr);

            messagingTemplate.convertAndSend(
                    "/topic/room/" + roomId,
                    state);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}