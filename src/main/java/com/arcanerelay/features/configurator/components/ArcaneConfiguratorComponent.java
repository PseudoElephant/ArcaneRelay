package com.arcanerelay.features.configurator.components;

import com.arcanerelay.ArcaneRelayPlugin;
import com.arcanerelay.features.configurator.util.VisualsUtil;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;

import org.joml.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nonnull;

/** Player component: tracks which Arcane Trigger blocks are being configured. */
public class ArcaneConfiguratorComponent implements Component<EntityStore> {

    /** Ordered map of selected block position to its display color index. */
    private final Map<Vector3i, Integer> selectedBlocks = new LinkedHashMap<>();

    public static ComponentType<EntityStore, ArcaneConfiguratorComponent> getComponentType() {
        return ArcaneRelayPlugin.get().getArcaneConfiguratorComponentType();
    }

    public Map<Vector3i, Integer> getSelectedBlocks() {
        return Collections.unmodifiableMap(selectedBlocks);
    }

    public boolean isSelected(Vector3i pos) {
        return selectedBlocks.containsKey(pos);
    }

    public int getColorIndex(Vector3i pos) {
        Integer index = selectedBlocks.get(pos);
        return index != null ? index : 0;
    }

    public void addSelectedBlock(Vector3i pos) {
        if (pos != null && !selectedBlocks.containsKey(pos)) {
            selectedBlocks.put(new Vector3i(pos), VisualsUtil.getNextColorIndex());
        }
    }

    public boolean removeSelectedBlock(Vector3i pos) {
        return pos != null && selectedBlocks.remove(pos) != null;
    }

    public void clearConfiguredBlocks() {
        selectedBlocks.clear();
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        ArcaneConfiguratorComponent clone = new ArcaneConfiguratorComponent();
        for (Map.Entry<Vector3i, Integer> entry : selectedBlocks.entrySet()) {
            clone.selectedBlocks.put(new Vector3i(entry.getKey()), entry.getValue());
        }
        return clone;
    }
}
