package me.playbosswar.tea.core.impl.map;

import org.jetbrains.annotations.Nullable;

/** Called after anything under {@code maps/} or {@code waiting-rooms/} was written. */
public final class MapChanges {

    private static volatile @Nullable Runnable listener;

    private MapChanges() {
    }

    public static void setListener(@Nullable Runnable r) {
        listener = r;
    }

    public static void changed() {
        me.playbosswar.tea.core.impl.cloud.CloudIntegration.mapsChanged();
        Runnable r = listener;
        if (r != null) {
            r.run();
        }
    }
}
