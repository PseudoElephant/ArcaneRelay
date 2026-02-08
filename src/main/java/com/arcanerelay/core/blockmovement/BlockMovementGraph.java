package com.arcanerelay.core.blockmovement;

import com.arcanerelay.ArcaneRelayPlugin;
import com.arcanerelay.state.ArcaneMoveState.MoveEntry;
import com.hypixel.hytale.math.vector.Vector3i;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds dependency and target-position graphs from move entries and computes
 * a topologically-ordered execution plan (collision-free steps).
 */
public final class BlockMovementGraph {

    private BlockMovementGraph() {}

    /**
     * Builds the dependency graph and target-position graph from move entries,
     * then returns an execution order: list of steps, each step is a list of
     * block positions that can be moved in parallel (no collisions).
     */
    @Nonnull
    public static List<List<Vector3i>> getExecutionOrder(
            @Nonnull Map<Vector3i, MoveEntry> moveEntries) {
        Map<Vector3i, List<Vector3i>> graph = buildDependencyGraph(moveEntries);
        Map<Vector3i, List<Vector3i>> targetPositionGraph = buildTargetPositionGraph(moveEntries);
        return simplifyByCollisions(graph, targetPositionGraph, moveEntries);
    }

    /**
     * Dependency graph: key = block position, value = list of positions that
     * depend on key (must move after key).
     */
    @Nonnull
    public static Map<Vector3i, List<Vector3i>> buildDependencyGraph(
            @Nonnull Map<Vector3i, MoveEntry> moveEntries) {
        Map<Vector3i, List<Vector3i>> graph = new HashMap<>();

        for (Map.Entry<Vector3i, MoveEntry> entry : moveEntries.entrySet()) {
            Vector3i blockPosition = entry.getKey();
            MoveEntry moveEntry = entry.getValue();
            if (!graph.containsKey(moveEntry.blockPosition)) {
                graph.put(moveEntry.blockPosition, new ArrayList<>());
            }
        }

        for (Map.Entry<Vector3i, MoveEntry> entry : moveEntries.entrySet()) {
            Vector3i blockPosition = entry.getKey();
            MoveEntry moveEntry = entry.getValue();
            Vector3i dependentBlockPosition = targetPosition(blockPosition, moveEntry);
            graph.computeIfAbsent(dependentBlockPosition, k -> new ArrayList<>()).add(blockPosition);
        }
        return graph;
    }

    /**
     * Target position graph: key = target position (where a block moves to),
     * value = list of source block positions that move to that target.
     */
    @Nonnull
    public static Map<Vector3i, List<Vector3i>> buildTargetPositionGraph(
            @Nonnull Map<Vector3i, MoveEntry> moveEntries) {
        Map<Vector3i, List<Vector3i>> targetPositionGraph = new HashMap<>();
        for (Map.Entry<Vector3i, MoveEntry> entry : moveEntries.entrySet()) {
            Vector3i blockPosition = entry.getKey();
            MoveEntry moveEntry = entry.getValue();
            Vector3i target = targetPosition(blockPosition, moveEntry);
            targetPositionGraph.computeIfAbsent(target, k -> new ArrayList<>()).add(blockPosition);
        }
        return targetPositionGraph;
    }

    @Nonnull
    static Vector3i targetPosition(@Nonnull Vector3i blockPosition, @Nonnull MoveEntry moveEntry) {
        return new Vector3i(
                blockPosition.x + moveEntry.moveDirection.x,
                blockPosition.y + moveEntry.moveDirection.y,
                blockPosition.z + moveEntry.moveDirection.z);
    }

    /**
     * Simplifies connected components by excluding positions that would collide
     * (multiple blocks moving to the same target). Returns execution order.
     */
    @Nonnull
    private static List<List<Vector3i>> simplifyByCollisions(
            @Nonnull Map<Vector3i, List<Vector3i>> graph,
            @Nonnull Map<Vector3i, List<Vector3i>> targetPositionGraph,
            @Nonnull Map<Vector3i, MoveEntry> moveEntries) {
        List<List<Vector3i>> connectedComponents = getConnectedComponents(graph);
        List<List<Vector3i>> result = new ArrayList<>();

        for (List<Vector3i> component : connectedComponents) {
            List<Vector3i> simplified = new ArrayList<>();
            for (Vector3i blockPosition : component) {
                MoveEntry moveEntry = moveEntries.get(blockPosition);
                if (moveEntry == null) continue;

                Vector3i target = targetPosition(blockPosition, moveEntry);
                List<Vector3i> targetsAt = targetPositionGraph.get(target);
                if (targetsAt == null) {
                    ArcaneRelayPlugin.get().getLogger().atInfo().log(
                            "BlockMovementGraph: no target blocks at " + target.x + "," + target.y + "," + target.z);
                    continue;
                }
                if (targetsAt.size() > 1) {
                    ArcaneRelayPlugin.get().getLogger().atInfo().log(
                            "BlockMovementGraph: collision at " + blockPosition.x + "," + blockPosition.y + ","
                                    + blockPosition.z + " (" + targetsAt.size() + " targets)");
                    continue;
                }
                simplified.add(blockPosition);
            }
            if (!simplified.isEmpty()) {
                result.add(simplified);
            }
        }
        return result;
    }

    @Nonnull
    private static List<List<Vector3i>> getConnectedComponents(
            @Nonnull Map<Vector3i, List<Vector3i>> graph) {
        List<List<Vector3i>> connected = new ArrayList<>();
        Set<Vector3i> visited = new HashSet<>();

        for (Map.Entry<Vector3i, List<Vector3i>> entry : graph.entrySet()) {
            if (visited.contains(entry.getKey())) continue;
            List<Vector3i> component = new ArrayList<>();
            topologicalSort(entry.getKey(), visited, graph, component);
            connected.add(component);
        }
        return connected;
    }

    private static void topologicalSort(
            @Nonnull Vector3i node,
            @Nonnull Set<Vector3i> visited,
            @Nonnull Map<Vector3i, List<Vector3i>> graph,
            @Nonnull List<Vector3i> out) {
        if (visited.contains(node)) return;
        visited.add(node);

        List<Vector3i> dependents = graph.get(node);
        if (dependents != null) {
            for (Vector3i dependent : dependents) {
                topologicalSort(dependent, visited, graph, out);
            }
        }
        out.add(node);
    }
}
