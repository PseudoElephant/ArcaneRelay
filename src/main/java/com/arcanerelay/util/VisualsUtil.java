package com.arcanerelay.util;

import javax.annotation.Nonnull;

import com.arcanerelay.components.ArcaneTriggerBlock;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.matrix.Matrix4d;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.DebugShape;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import javax.annotation.Nullable;

public class VisualsUtil {
    static final float FADE_TIME = 12f;
    static final double LINE_THICKNESS = 0.025;
    static final int DEBUG_FLAGS = DebugUtils.FLAG_FADE | DebugUtils.FLAG_NO_WIREFRAME;
    static final double CORNER_SIZE = 0.20;
    static final double INNER_BOX_GAP = 0.075; 

    static int colorIndex = 0;
    static Vector3f[] colors = new Vector3f[] {
        new Vector3f(0.2f, 1f, 1f),
        new Vector3f(0.2f, 1f, 0.5f),
        new Vector3f(0.45f, 1f, 0.2f),
        new Vector3f(0.75f, 1f, 0.2f),
        new Vector3f(1f, 0.95f, 0.2f),
        new Vector3f(1f, 0.8f, 0.2f),
        new Vector3f(1f, 0.55f, 0.2f),
        new Vector3f(1f, 0.35f, 0.2f),
        new Vector3f(1f, 0.2f, 0.38f),
        new Vector3f(0.95f, 0.2f, 1f),
        new Vector3f(0.78f, 0.2f, 1f),
        new Vector3f(0.62f, 0.2f, 1f),
        new Vector3f(0.45f, 0.2f, 1f),
        new Vector3f(0.2f, 0.4f, 1f),
        new Vector3f(0.2f, 0.6f, 1f),
        new Vector3f(0.2f, 0.8f, 1f)
    };

    public static void displayTriggerConnections(World world, Vector3i triggerPos) {
        boolean cycleColor = false;
        displayTriggerConnections(world, triggerPos, cycleColor);
    }

    public static void displayTriggerConnections(World world, Vector3i triggerPos, boolean cycleColor) {
        if (cycleColor) {
            cycleColor();
        }

        showTriggerOutputArrows(world, triggerPos);
        showOutputWireframes(world, triggerPos);
        showInputWireframe(world, triggerPos);
    }

    private static void cycleColor() {
        colorIndex = (colorIndex + 1) % colors.length;
    }

    private static void showInputWireframe(World world, Vector3i triggerPos) {
        WorldChunk chunk = world.getChunk(ChunkUtil.indexChunkFromBlock(triggerPos.x, triggerPos.z));
        if (chunk == null) return;

        Ref<ChunkStore> blockRef = chunk.getBlockComponentEntity(triggerPos.x, triggerPos.y, triggerPos.z);
        if (blockRef == null || !blockRef.isValid()) return;

        Store<ChunkStore> store = world.getChunkStore().getStore();
        ArcaneTriggerBlock triggerBlock = store.getComponent(blockRef, ArcaneTriggerBlock.getComponentType());
        if (triggerBlock == null) return;

        Box box = getEnclosingBoundingHitbox(world, triggerPos);
        if (box == null) return;

        Vector3f color = colors[colorIndex];
        if (triggerBlock.isUsingRelativeOutput()) {
            DebugVisualsCustomShapes.drawCenterBoxesInWireframe(world, box, triggerPos, color);
        } 
        
        DebugVisualsCustomShapes.drawBoxWireframe(world, box, triggerPos, color);
    }

    private static void showOutputWireframes(World world, Vector3i triggerPos) {
        WorldChunk chunk = world.getChunk(ChunkUtil.indexChunkFromBlock(triggerPos.x, triggerPos.z));
        if (chunk == null) return;

        Ref<ChunkStore> blockRef = chunk.getBlockComponentEntity(triggerPos.x, triggerPos.y, triggerPos.z);
        if (blockRef == null || !blockRef.isValid()) return;

        Store<ChunkStore> store = world.getChunkStore().getStore();
        ArcaneTriggerBlock triggerBlock = store.getComponent(blockRef, ArcaneTriggerBlock.getComponentType());
        if (triggerBlock == null || !triggerBlock.hasOutputPositions()) return;

        Vector3f color = colors[colorIndex];
        for (Vector3i out : triggerBlock.getGlobalOutputPositions(triggerPos)) {
            Box box = getEnclosingBoundingHitbox(world, out);
            if (box != null) {
                DebugVisualsCustomShapes.drawCornerOnlyBoxWireframe(world, box, out, color);
            }
        }
    }

    /** Draw debug arrows from trigger to each output; call after updating trigger outputs (e.g. from AddOutputInteraction). */
    private static void showTriggerOutputArrows(World world, Vector3i triggerPos) {
        WorldChunk chunk = world.getChunk(ChunkUtil.indexChunkFromBlock(triggerPos.x, triggerPos.z));
        if (chunk == null) return;

        Ref<ChunkStore> blockRef = chunk.getBlockComponentEntity(triggerPos.x, triggerPos.y, triggerPos.z);
        if (blockRef == null || !blockRef.isValid()) return;

        Store<ChunkStore> store = world.getChunkStore().getStore();
        ArcaneTriggerBlock triggerBlock = store.getComponent(blockRef, ArcaneTriggerBlock.getComponentType());
        if (triggerBlock == null || !triggerBlock.hasOutputPositions()) return;

        Vector3d from = new Vector3d(triggerPos.x + 0.5, triggerPos.y + 0.5, triggerPos.z + 0.5);
        Vector3f color = colors[colorIndex];
        for (Vector3i out : triggerBlock.getGlobalOutputPositions(triggerPos)) {
            Vector3d to = new Vector3d(out.x + 0.5, out.y + 0.5, out.z + 0.5);
            Vector3d direction = to.clone().subtract(from);
            
            if (direction.squaredLength() < 0.01) continue;

            DebugVisualsCustomShapes.drawArrow(world, from, direction, color, LINE_THICKNESS * 2, FADE_TIME / 4, DEBUG_FLAGS); // 2 and 4 chosen just because they felt right.
        }
    }

    private static Box getEnclosingBoundingHitbox(@Nonnull World world, @Nonnull Vector3i blockPos) {
        WorldChunk chunk = world.getChunk(ChunkUtil.indexChunkFromBlock(blockPos.x, blockPos.z));
        if (chunk == null) return null;

        BlockType blockType = chunk.getBlockType(blockPos.x, blockPos.y, blockPos.z);
        if (blockType == null) return null;

        int rotationIndex = chunk.getRotationIndex(blockPos.x, blockPos.y, blockPos.z);
        int hitboxTypeIndex = blockType.getHitboxTypeIndex();
        
        BlockBoundingBoxes hitboxAsset = BlockBoundingBoxes.getAssetMap().getAsset(hitboxTypeIndex);
        if (hitboxAsset == null) return null;

        BlockBoundingBoxes.RotatedVariantBoxes rotatedVariant = hitboxAsset.get(rotationIndex);
        if (rotatedVariant == null) return null;

        return createEnclosingBoundingBox(rotatedVariant.getDetailBoxes());
    }

    // Creates a single minimum bounding box around all boxes in the array.
    private static Box createEnclosingBoundingBox(@Nullable Box[] boxes) {
        if (boxes == null || boxes.length == 0) {
            return null;
        }

        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;

        for (Box box : boxes) {
            if (box == null) continue;
            
            minX = Math.min(minX, box.min.x);
            minY = Math.min(minY, box.min.y);
            minZ = Math.min(minZ, box.min.z);
            
            maxX = Math.max(maxX, box.max.x);
            maxY = Math.max(maxY, box.max.y);
            maxZ = Math.max(maxZ, box.max.z);
        }

        // If no valid boxes were found, return null
        if (minX == Double.POSITIVE_INFINITY || maxX == Double.NEGATIVE_INFINITY) {
            return null;
        }

        Box enclosingBox = new Box();
        enclosingBox.setMinMax(new Vector3d(minX, minY, minZ), new Vector3d(maxX, maxY, maxZ));
        return enclosingBox;
    }

    private class DebugVisualsCustomShapes {
        private static void drawLine(@Nonnull World world, @Nonnull Vector3d start, @Nonnull Vector3d end, @Nonnull Vector3f color) {
            DebugVisualsCustomShapes.drawLine(world, start, end, color, LINE_THICKNESS);
        }

        private static void drawLine(@Nonnull World world, @Nonnull Vector3d start, @Nonnull Vector3d end, @Nonnull Vector3f color, double thickness) {
            DebugUtils.addLine(world, start, end, color, thickness, FADE_TIME, DEBUG_FLAGS);
        }

        public static void drawCenterBoxesInWireframe(@Nonnull World world, @Nonnull Box box, @Nonnull Vector3i blockPos, @Nonnull Vector3f color) {
            double minX = box.min.x + blockPos.x, minY = box.min.y + blockPos.y, minZ = box.min.z + blockPos.z;
            double maxX = box.max.x + blockPos.x, maxY = box.max.y + blockPos.y, maxZ = box.max.z + blockPos.z;
            double gap = INNER_BOX_GAP;
            
            // Bottom face
            drawCenteredRectangle(world, minX + gap, maxX - gap, minZ + gap, maxZ - gap, minY, 0, color);
            
            // Top face
            drawCenteredRectangle(world, minX + gap, maxX - gap, minZ + gap, maxZ - gap, maxY, 0, color);
            
            // Front face
            drawCenteredRectangle(world, minX + gap, maxX - gap, minY + gap, maxY - gap, minZ, 1, color);
            
            // Back face
            drawCenteredRectangle(world, minX + gap, maxX - gap, minY + gap, maxY - gap, maxZ, 1, color);
            
            // Left face
            drawCenteredRectangle(world, minY + gap, maxY - gap, minZ + gap, maxZ - gap, minX, 2, color);
            
            // Right face
            drawCenteredRectangle(world, minY + gap, maxY - gap, minZ + gap, maxZ - gap, maxX, 2, color);
        }
        
        private static void drawCenteredRectangle(@Nonnull World world, double c1min, double c1max, double c2min, double c2max, double fixed, int plane, @Nonnull Vector3f color) {
            Vector3d p1, p2, p3, p4;
            
            if (plane == 0) { // XZ plane constant Y
                p1 = new Vector3d(c1min, fixed, c2min);
                p2 = new Vector3d(c1max, fixed, c2min);
                p3 = new Vector3d(c1max, fixed, c2max);
                p4 = new Vector3d(c1min, fixed, c2max);
            } else if (plane == 1) { // XY plane constant Z
                p1 = new Vector3d(c1min, c2min, fixed);
                p2 = new Vector3d(c1max, c2min, fixed);
                p3 = new Vector3d(c1max, c2max, fixed);
                p4 = new Vector3d(c1min, c2max, fixed);
            } else { // plane == 2, YZ plane constant X
                p1 = new Vector3d(fixed, c1min, c2min);
                p2 = new Vector3d(fixed, c1max, c2min);
                p3 = new Vector3d(fixed, c1max, c2max);
                p4 = new Vector3d(fixed, c1min, c2max);
            }
            
            int thinFactor = 3; 
            drawLine(world, p1, p2, color, LINE_THICKNESS / thinFactor);
            drawLine(world, p2, p3, color, LINE_THICKNESS / thinFactor);
            drawLine(world, p3, p4, color, LINE_THICKNESS / thinFactor);
            drawLine(world, p4, p1, color, LINE_THICKNESS / thinFactor);
        }

        public static void drawBoxWireframe(@Nonnull World world, @Nonnull Box box, @Nonnull Vector3i blockPos, @Nonnull Vector3f color) {
            double minX = box.min.x + blockPos.x, minY = box.min.y + blockPos.y, minZ = box.min.z + blockPos.z;
            double maxX = box.max.x + blockPos.x, maxY = box.max.y + blockPos.y, maxZ = box.max.z + blockPos.z;

            // Bottom face
            drawLine(world, new Vector3d(minX, minY, minZ), new Vector3d(maxX, minY, minZ), color);
            drawLine(world, new Vector3d(maxX, minY, minZ), new Vector3d(maxX, minY, maxZ), color);
            drawLine(world, new Vector3d(maxX, minY, maxZ), new Vector3d(minX, minY, maxZ), color);
            drawLine(world, new Vector3d(minX, minY, maxZ), new Vector3d(minX, minY, minZ), color);

            // Top face
            drawLine(world, new Vector3d(minX, maxY, minZ), new Vector3d(maxX, maxY, minZ), color);
            drawLine(world, new Vector3d(maxX, maxY, minZ), new Vector3d(maxX, maxY, maxZ), color);
            drawLine(world, new Vector3d(maxX, maxY, maxZ), new Vector3d(minX, maxY, maxZ), color);
            drawLine(world, new Vector3d(minX, maxY, maxZ), new Vector3d(minX, maxY, minZ), color);

            // Vertical pillars
            drawLine(world, new Vector3d(minX, minY, minZ), new Vector3d(minX, maxY, minZ), color);
            drawLine(world, new Vector3d(maxX, minY, minZ), new Vector3d(maxX, maxY, minZ), color);
            drawLine(world, new Vector3d(maxX, minY, maxZ), new Vector3d(maxX, maxY, maxZ), color);
            drawLine(world, new Vector3d(minX, minY, maxZ), new Vector3d(minX, maxY, maxZ), color);
        }

        public static void drawCornerOnlyBoxWireframe(@Nonnull World world, @Nonnull Box box, @Nonnull Vector3i blockPos, @Nonnull Vector3f color) {
            double minX = box.min.x + blockPos.x, minY = box.min.y + blockPos.y, minZ = box.min.z + blockPos.z;
            double maxX = box.max.x + blockPos.x, maxY = box.max.y + blockPos.y, maxZ = box.max.z + blockPos.z;

            double cornerSize = CORNER_SIZE;
            while (cornerSize > 0 && 
                    (cornerSize * 2.5 > (maxX - minX) 
                    || cornerSize * 2.5 > (maxY - minY) 
                    || cornerSize * 2.5 > (maxZ - minZ))
            ) {
                cornerSize /= 2; // Shrink corner size if the box is too small
            }

            Vector3d[] corners = {
                new Vector3d(minX, minY, minZ),
                new Vector3d(maxX, minY, minZ),
                new Vector3d(maxX, minY, maxZ),
                new Vector3d(minX, minY, maxZ),
                new Vector3d(minX, maxY, minZ),
                new Vector3d(maxX, maxY, minZ),
                new Vector3d(maxX, maxY, maxZ),
                new Vector3d(minX, maxY, maxZ)
            };

            for (Vector3d corner : corners) {
                // Determine direction based on which side of the box the corner is on
                double dirX = corner.x == minX ? cornerSize : -cornerSize;
                double dirY = corner.y == minY ? cornerSize : -cornerSize;
                double dirZ = corner.z == minZ ? cornerSize : -cornerSize;

                drawLine(world, corner, new Vector3d(corner.x + dirX, corner.y, corner.z), color);
                drawLine(world, corner, new Vector3d(corner.x, corner.y + dirY, corner.z), color);
                drawLine(world, corner, new Vector3d(corner.x, corner.y, corner.z + dirZ), color);
            }
        }

        public static void drawArrow(@Nonnull World world, @Nonnull Vector3d position, @Nonnull Vector3d direction, @Nonnull Vector3f color, double thickness, float time, int flags) {
            drawArrow(world, position, direction, color, 0.8F, time, flags, thickness);
        }

        // This is a modified copy of DebugUtils.drawArrow that allows for custom thickness.
        private static void drawArrow(@Nonnull World world, @Nonnull Vector3d position, @Nonnull Vector3d direction, @Nonnull Vector3f color, float opacity, float time, int flags, double thickness) {
            Vector3d directionClone = direction.clone();
            Matrix4d tmp = new Matrix4d();
            Matrix4d matrix = new Matrix4d();
            matrix.identity();
            matrix.translate(position);
            double angleY = Math.atan2(directionClone.z, directionClone.x);
            matrix.rotateAxis(angleY + (Math.PI / 2D), (double)0.0F, (double)1.0F, (double)0.0F, tmp);
            double angleX = Math.atan2(Math.sqrt(directionClone.x * directionClone.x + directionClone.z * directionClone.z), directionClone.y);
            matrix.rotateAxis(angleX, (double)1.0F, (double)0.0F, (double)0.0F, tmp);
            drawArrow(world, matrix, color, opacity, directionClone.length(), time, flags, thickness);
        }

        // This is a modified copy of DebugUtils.drawArrow that allows for custom thickness.
        private static void drawArrow(@Nonnull World world, @Nonnull Matrix4d baseMatrix, @Nonnull Vector3f color, float opacity, double length, float time, int flags, double thickness) {
            double adjustedLength = length - thickness * 3.0;
            if (adjustedLength > (double)0.0F) {
                Matrix4d matrix = new Matrix4d(baseMatrix);
                matrix.translate((double)0.0F, adjustedLength * (double)0.5F, (double)0.0F);
                matrix.scale(thickness, adjustedLength, thickness);
                DebugUtils.add(world, DebugShape.Cylinder, matrix, color, time, flags);
            }

            Matrix4d matrix = new Matrix4d(baseMatrix);
            matrix.translate((double)0.0F, adjustedLength + thickness * 1.5, (double)0.0F);
            matrix.scale(thickness * 3.0, thickness * 3.0, thickness * 3.0);
            DebugUtils.add(world, DebugShape.Cone, matrix, color, opacity, time, flags);
        }
    }
}
