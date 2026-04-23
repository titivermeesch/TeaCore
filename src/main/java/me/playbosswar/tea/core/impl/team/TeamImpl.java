package me.playbosswar.tea.core.impl.team;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.playbosswar.tea.core.api.team.Team;
import me.playbosswar.tea.core.api.team.TeamId;

public final class TeamImpl implements Team {

    private final TeamId id;
    private final String displayNameKey;
    private final String colorStyleSlot;
    private final int maxSize;
    private final Set<UUID> members = ConcurrentHashMap.newKeySet();

    public TeamImpl(TeamId id, String displayNameKey, String colorStyleSlot, int maxSize) {
        this.id = id;
        this.displayNameKey = displayNameKey;
        this.colorStyleSlot = colorStyleSlot;
        this.maxSize = maxSize;
    }

    @Override
    public TeamId id() {
        return id;
    }

    @Override
    public String displayNameKey() {
        return displayNameKey;
    }

    @Override
    public String colorStyleSlot() {
        return colorStyleSlot;
    }

    @Override
    public Set<UUID> members() {
        return Set.copyOf(members);
    }

    @Override
    public int maxSize() {
        return maxSize;
    }

    public boolean add(UUID uuid) {
        return members.size() < maxSize && members.add(uuid);
    }

    public boolean remove(UUID uuid) {
        return members.remove(uuid);
    }

    public int size() {
        return members.size();
    }
}
