package com.arcanerelay.state;

import com.hypixel.hytale.math.vector.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A trigger at target position with source position.
 * When {@code skip} is true, the block is not activated—only its outputs are propagated.
 * When {@code activatorId} is non-null, that activation is run instead of the block's binding (skip is false).
 */
public record TriggerEntry(@Nonnull Vector3i target, @Nonnull Vector3i source, boolean skip, @Nullable String activatorId) {

    @Nonnull
    public static TriggerEntry of(@Nonnull Vector3i target, @Nonnull Vector3i source, boolean skip) {
        return new TriggerEntry(target, source, skip, null);
    }

    @Nonnull
    public static TriggerEntry of(@Nonnull Vector3i target, @Nonnull Vector3i source, boolean skip, @Nullable String activatorId) {
        return new TriggerEntry(target, source, skip, activatorId);
    }

    @Nonnull
    public static TriggerEntry of(@Nonnull Vector3i target, @Nonnull Vector3i source) {
        return of(target, source, false);
    }

    @Nonnull
    public static TriggerEntry of(int x, int y, int z, int sourceX, int sourceY, int sourceZ, boolean skip) {
        return of(new Vector3i(x, y, z), new Vector3i(sourceX, sourceY, sourceZ), skip, null);
    }

    @Nonnull
    public static TriggerEntry of(int x, int y, int z, int sourceX, int sourceY, int sourceZ, boolean skip, @Nullable String activatorId) {
        return of(new Vector3i(x, y, z), new Vector3i(sourceX, sourceY, sourceZ), skip, activatorId);
    }

    @Nonnull
    public static TriggerEntry of(int x, int y, int z, int sourceX, int sourceY, int sourceZ) {
        return of(x, y, z, sourceX, sourceY, sourceZ, false);
    }
}
