package com.bubblechat;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

public class RpChannel {
    public String pin;
    @Nullable public UUID hostUuid;
    public Set<UUID> members = ConcurrentHashMap.newKeySet();
    public long createdAt;

    public RpChannel(String pin, @Nullable UUID hostUuid) {
        this.pin = pin;
        this.hostUuid = hostUuid;
        this.createdAt = System.currentTimeMillis();
        if (hostUuid != null) members.add(hostUuid);
    }

    /** For Gson deserialization. */
    public RpChannel() {
        this.pin = "";
        this.createdAt = System.currentTimeMillis();
    }

    public boolean isMember(UUID uuid) {
        return members.contains(uuid);
    }

    public boolean isEmpty() {
        return members.isEmpty();
    }
}
