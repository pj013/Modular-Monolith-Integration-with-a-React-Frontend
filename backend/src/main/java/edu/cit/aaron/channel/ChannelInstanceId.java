package edu.cit.aaron.channel;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public record ChannelInstanceId(UUID value) {

    public ChannelInstanceId() {
        this(UUID.randomUUID());
    }
}
