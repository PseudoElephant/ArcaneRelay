package com.pseudoelephant.arcanerelay.features.config;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.validator.RangeValidator;

public class ArcaneRelayConfig {
    @Nonnull 
    public static final BuilderCodec<ArcaneRelayConfig> CODEC = BuilderCodec
        .builder(ArcaneRelayConfig.class, ArcaneRelayConfig::new)
        .documentation("Configuration file for Arcane Relay.")

        .append(new KeyedCodec<Integer>("RelayDistance", Codec.INTEGER),
                (config, value, info) -> config.relayDistance = value,
                (config, info) -> config.relayDistance)
        .documentation("Maximum distance at which the Arcane Staff can succesfully create a connection.")
        .addValidator(new RangeValidator<Integer>(0, Integer.MAX_VALUE, false))
        .add()

        .append(new KeyedCodec<Integer>("PusherRange", Codec.INTEGER),
                (config, value, info) -> config.pusherRange = value,
                (config, info) -> config.pusherRange)
        .documentation("Maximum number of blocks the pusher is allowed to succesfully block before being blocked.")
        .addValidator(new RangeValidator<Integer>(0, Integer.MAX_VALUE, true))
        .add()

        .append(new KeyedCodec<Integer>("PullerRange", Codec.INTEGER),
                (config, value, info) -> config.pullerRange = value,
                (config, info) -> config.pullerRange)
        .documentation("The range at which the Puller will extend to before starting to contract.")
        .addValidator(new RangeValidator<Integer>(0, Integer.MAX_VALUE, false))
        .add()

        .append(new KeyedCodec<Float>("BreakerEntityDamage", Codec.FLOAT),
                (config, value, info) -> config.breakerEntityDamage = value,
                (config, info) -> config.breakerEntityDamage)
        .documentation("The damage dealt by the Breaker to entities.")
        .addValidator(new RangeValidator<Float>(0.0f, Float.MAX_VALUE, true))
        .add()

        .append(new KeyedCodec<Float>("BreakerBlockDamageScalar", Codec.FLOAT),
                (config, value, info) -> config.breakerBlockDamageScalar = value,
                (config, info) -> config.breakerBlockDamageScalar)
        .documentation("The damage scalar applied to the damage the Breaker does to blocks.")
        .addValidator(new RangeValidator<Float>(0.0f, Float.MAX_VALUE, true))
        .add()

        .append(new KeyedCodec<String[]>("MoveableBlacklist", Codec.STRING_ARRAY),
                (config, value, info) -> config.noneMoveableBlocks = value != null ? new HashSet<>(Arrays.asList(value)) : new HashSet<>(),
                (config, info) -> config.noneMoveableBlocks.toArray(new String[0]))
        .documentation("List of blocks that cannot be moved.")
        .add()

        .append(new KeyedCodec<String[]>("RotatableBlacklist", Codec.STRING_ARRAY),
                (config, value, info) -> config.noneRotatableBlocks = value != null ? new HashSet<>(Arrays.asList(value)) : new HashSet<>(),
                (config, info) -> config.noneRotatableBlocks.toArray(new String[0]))
        .documentation("List of blocks that cannot be rotated.")
        .add()

        .build();

    private static final int DEFAULT_RELAY_DISTANCE = 10;
    private static final int DEFAULT_PUSHER_RANGE = 15;
    private static final int DEFAULT_PULLER_RANGE = 15;
    private static final float DEFAULT_BREAKER_ENTITY_DAMAGE = 5.0f;
    private static final float DEFAULT_BREAKER_BLOCK_DAMAGE_SCALAR = 1.0f;
    private static final String[] DEFAULT_NONE_MOVEABLE = { "Barrier", "Bedrock" };
    private static final String[] DEFAULT_NONE_ROTATABLE = { "Bench", "Bed", "Barrier", "Bedrock" };

    private int relayDistance;
    private int pusherRange;
    private int pullerRange;
    private float breakerEntityDamage;
    private float breakerBlockDamageScalar;
    private Set<String> noneMoveableBlocks;
    private Set<String> noneRotatableBlocks;

    public ArcaneRelayConfig() {
        resetToDefaults();
    }

    public ArcaneRelayConfig(ArcaneRelayConfig other) {
        copyFrom(other);
    }

    public void resetToDefaults() {
        this.relayDistance = DEFAULT_RELAY_DISTANCE;
        this.pusherRange = DEFAULT_PUSHER_RANGE;
        this.pullerRange = DEFAULT_PULLER_RANGE;
        this.breakerEntityDamage = DEFAULT_BREAKER_ENTITY_DAMAGE;
        this.breakerBlockDamageScalar = DEFAULT_BREAKER_BLOCK_DAMAGE_SCALAR;
        this.noneMoveableBlocks = new HashSet<>(Arrays.asList(DEFAULT_NONE_MOVEABLE));
        this.noneRotatableBlocks = new HashSet<>(Arrays.asList(DEFAULT_NONE_ROTATABLE));
    }

    public void copyFrom(ArcaneRelayConfig other) {
        if (other == null) return;
        this.relayDistance = other.relayDistance;
        this.pusherRange = other.pusherRange;
        this.pullerRange = other.pullerRange;
        this.breakerEntityDamage = other.breakerEntityDamage;
        this.breakerBlockDamageScalar = other.breakerBlockDamageScalar;
        this.noneMoveableBlocks = other.noneMoveableBlocks != null ? new HashSet<>(other.noneMoveableBlocks) : new HashSet<>();
        this.noneRotatableBlocks = other.noneRotatableBlocks != null ? new HashSet<>(other.noneRotatableBlocks) : new HashSet<>();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        ArcaneRelayConfig that = (ArcaneRelayConfig) o;
        return relayDistance == that.relayDistance &&
                pusherRange == that.pusherRange &&
                pullerRange == that.pullerRange &&
                Float.compare(that.breakerEntityDamage, breakerEntityDamage) == 0 &&
                Float.compare(that.breakerBlockDamageScalar, breakerBlockDamageScalar) == 0 &&
                Objects.equals(noneMoveableBlocks, that.noneMoveableBlocks) &&
                Objects.equals(noneRotatableBlocks, that.noneRotatableBlocks);
    }

    // Getters
    public int getRelayDistance() {
        return relayDistance;
    }

    public int getPusherRange() {
        return pusherRange;
    }

    public int getPullerRange() {
        return pullerRange;
    }

    public float getBreakerEntityDamage() {
        return breakerEntityDamage;
    }

    public float getBreakerBlockDamageScalar() {
        return breakerBlockDamageScalar;
    }

    public Set<String> getNoneMoveableBlocks() {
        return noneMoveableBlocks;
    }

    public Set<String> getNoneRotatableBlocks() {
        return noneRotatableBlocks;
    }

    public void setRelayDistance(int relayDistance) { this.relayDistance = relayDistance; }
    public void setPusherRange(int pusherRange) { this.pusherRange = pusherRange; }
    public void setPullerRange(int pullerRange) { this.pullerRange = pullerRange; }
    public void setBreakerEntityDamage(float breakerEntityDamage) { this.breakerEntityDamage = breakerEntityDamage; }
    public void setBreakerBlockDamageScalar(float breakerBlockDamageScalar) { this.breakerBlockDamageScalar = breakerBlockDamageScalar; }
    public void setNoneMoveableBlocks(Set<String> noneMoveableBlocks) { this.noneMoveableBlocks = noneMoveableBlocks; }
    public void setNoneRotatableBlocks(Set<String> noneRotatableBlocks) { this.noneRotatableBlocks = noneRotatableBlocks; }
}