package com.arcanerelay.externalplugins;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class HexcodeBridge extends ExternalPluginBridge {
    private static final String IMBUED_BLOCK_ACTIVATOR =
            "com.riprod.hexcode.core.common.imbuement.block.ImbuedBlockActivator";
    private static final String IMBUED_BLOCK_COMPONENT =
            "com.riprod.hexcode.core.common.imbuement.component.ImbuedBlockComponent";

    private static final Object TRY_CONSUME_LOCK = new Object();
    @Nullable private static Method tryConsumeMethod;
    private static boolean tryConsumeResolveAttempted;

    private static final Object IMBUED_COMPONENT_TYPE_LOCK = new Object();
    @Nullable private static Method imbuedBlockGetComponentTypeMethod;
    private static boolean imbuedBlockGetComponentTypeResolveAttempted;

    private HexcodeBridge() {
    }

    /** {@code true} when Hexcode classes are on the classpath (does not guarantee API shape). */
    public static boolean isAvailable() {
        return isClassAvailable(IMBUED_BLOCK_ACTIVATOR);
    }

    /**
     * Reflectively calls {@code ImbuedBlockComponent.getComponentType()} when Hexcode is present.
     * Returns {@code null} if the mod is absent, the type is not registered yet, or reflection fails.
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public static ComponentType<ChunkStore, ?> getImbuedBlockComponentType() {
        Method m = resolveImbuedBlockGetComponentTypeMethod();
        if (m == null) return null;
        try {
            Object type = m.invoke(null);
            if (!(type instanceof ComponentType)) return null;
            return (ComponentType<ChunkStore, ?>) type;
        } catch (ReflectiveOperationException | ClassCastException e) {
            return null;
        }
    }

    @Nullable
    private static Method resolveImbuedBlockGetComponentTypeMethod() {
        Method cached = imbuedBlockGetComponentTypeMethod;
        if (cached != null) return cached;
        synchronized (IMBUED_COMPONENT_TYPE_LOCK) {
            if (imbuedBlockGetComponentTypeMethod != null) return imbuedBlockGetComponentTypeMethod;
            if (imbuedBlockGetComponentTypeResolveAttempted) return null;
            imbuedBlockGetComponentTypeResolveAttempted = true;
            try {
                Class<?> clazz = Class.forName(IMBUED_BLOCK_COMPONENT);
                Method found = clazz.getMethod("getComponentType");
                if (!Modifier.isStatic(found.getModifiers())) return null;
                if (found.getParameterCount() != 0) return null;
                if (!ComponentType.class.isAssignableFrom(found.getReturnType())) return null;
                imbuedBlockGetComponentTypeMethod = found;
                return found;
            } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                return null;
            }
        }
    }

    /**
     * Reflectively invokes {@code ImbuedBlockActivator.tryConsume} when Hexcode is present.
     * If the mod is absent, status is {@link ActivationStatus#PLUGIN_UNAVAILABLE}.
     */
    @Nonnull
    public static ActivationOutcome tryConsume(
            @Nonnull CommandBuffer<EntityStore> buffer,
            @Nonnull World world,
            @Nonnull Vector3i blockPos) {
        Method m = resolveTryConsumeMethod();
        if (m == null) {
            return ActivationOutcome.unavailable();
        }
        try {
            Object raw = m.invoke(null, buffer, world, blockPos);
            return ActivationOutcome.fromRemote(raw);
        } catch (InvocationTargetException e) {
            return ActivationOutcome.error();
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            return ActivationOutcome.error();
        }
    }

    @Nullable
    private static Method resolveTryConsumeMethod() {
        Method cached = tryConsumeMethod;
        if (cached != null) return cached;
        synchronized (TRY_CONSUME_LOCK) {
            if (tryConsumeMethod != null) return tryConsumeMethod;
            if (tryConsumeResolveAttempted) return null;
            tryConsumeResolveAttempted = true;
            try {
                Class<?> clazz = Class.forName(IMBUED_BLOCK_ACTIVATOR);
                Method found = findTryConsumeStatic(clazz);
                tryConsumeMethod = found;
                return found;
            } catch (ClassNotFoundException ignored) {
                return null;
            }
        }
    }

    @Nullable
    private static Method findTryConsumeStatic(@Nonnull Class<?> activatorClass) {
        for (Method method : activatorClass.getMethods()) {
            if (!"tryConsume".equals(method.getName())) continue;
            if (!Modifier.isStatic(method.getModifiers())) continue;
            Class<?>[] p = method.getParameterTypes();
            if (p.length != 3) continue;
            if (!p[0].isAssignableFrom(CommandBuffer.class)) continue;
            if (!p[1].isAssignableFrom(World.class)) continue;
            if (!p[2].isAssignableFrom(Vector3i.class)) continue;
            return method;
        }
        return null;
    }

    public enum ActivationStatus {
        READY_FROM_SLOT,
        READY_FROM_ESSENCE,
        NO_HEX,
        NO_SLOT_NO_ESSENCE,
        EXECUTION_FAILED,
        PLUGIN_UNAVAILABLE,
        REFLECTION_ERROR
    }

    public static final class ActivationOutcome {
        private final ActivationStatus status;
        @Nullable private final Object remoteCastData;

        private ActivationOutcome(ActivationStatus status, @Nullable Object remoteCastData) {
            this.status = status;
            this.remoteCastData = remoteCastData;
        }

        @Nonnull
        public ActivationStatus getStatus() {
            return status;
        }

        @Nullable
        public Object getRemoteCastData() {
            return remoteCastData;
        }

        public boolean isReady() {
            return status == ActivationStatus.READY_FROM_SLOT
                    || status == ActivationStatus.READY_FROM_ESSENCE;
        }

        @Nonnull
        private static ActivationOutcome unavailable() {
            return new ActivationOutcome(ActivationStatus.PLUGIN_UNAVAILABLE, null);
        }

        @Nonnull
        private static ActivationOutcome error() {
            return new ActivationOutcome(ActivationStatus.REFLECTION_ERROR, null);
        }

        @Nonnull
        private static ActivationOutcome fromRemote(@Nullable Object rawOutcome) {
            if (rawOutcome == null) {
                return new ActivationOutcome(ActivationStatus.REFLECTION_ERROR, null);
            }
            try {
                Method getStatus = rawOutcome.getClass().getMethod("getStatus");
                Object statusObj = getStatus.invoke(rawOutcome);
                ActivationStatus status = mapRemoteStatus(statusObj);

                Object castData = null;
                try {
                    Method getCastData = rawOutcome.getClass().getMethod("getCastData");
                    castData = getCastData.invoke(rawOutcome);
                } catch (NoSuchMethodException ignored) {
                }
                return new ActivationOutcome(status, castData);
            } catch (ReflectiveOperationException e) {
                return new ActivationOutcome(ActivationStatus.REFLECTION_ERROR, null);
            }
        }

        @Nonnull
        private static ActivationStatus mapRemoteStatus(@Nullable Object statusObj) {
            if (!(statusObj instanceof Enum<?> remote)) {
                return ActivationStatus.REFLECTION_ERROR;
            }
            try {
                return ActivationStatus.valueOf(remote.name());
            } catch (IllegalArgumentException e) {
                return ActivationStatus.REFLECTION_ERROR;
            }
        }
    }
}
