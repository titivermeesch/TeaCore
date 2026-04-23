package me.playbosswar.tea.core.impl.profile;

import java.time.Instant;
import java.util.UUID;
import me.playbosswar.tea.core.api.profile.PlayerProfile;
import org.jetbrains.annotations.Nullable;

public final class PlayerProfileImpl implements PlayerProfile {

    private final UUID uuid;
    private final Instant firstSeen;
    private volatile Instant lastSeen;
    private volatile @Nullable String locale;

    public PlayerProfileImpl(UUID uuid, Instant firstSeen, Instant lastSeen, @Nullable String locale) {
        this.uuid = uuid;
        this.firstSeen = firstSeen;
        this.lastSeen = lastSeen;
        this.locale = locale;
    }

    @Override
    public UUID uuid() {
        return uuid;
    }

    @Override
    public Instant firstSeen() {
        return firstSeen;
    }

    @Override
    public Instant lastSeen() {
        return lastSeen;
    }

    @Override
    public @Nullable String locale() {
        return locale;
    }

    public void setLastSeen(Instant lastSeen) {
        this.lastSeen = lastSeen;
    }

    public void setLocale(@Nullable String locale) {
        this.locale = locale;
    }
}
