package dev.statesecurity.smartnametags.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.GlassBlock;
import net.minecraft.block.StainedGlassBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(EntityRenderer.class)
public abstract class RenderNameTagMixin<T extends Entity> {
    @Inject(at = @At("HEAD"), method = "render", cancellable = true)
    private void doNotRender(T entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (entity instanceof PlayerEntity) {
            PlayerEntity localPlayer = MinecraftClient.getInstance().player;
            PlayerEntity targetPlayer = (PlayerEntity) entity;

            // Check if the local player can see the target player
            boolean canSeeTarget = localPlayer != null && localPlayer != targetPlayer && isPlayerVisible(localPlayer, targetPlayer);

            // Check if the local player is in F5 mode and the target player is behind
            boolean isF5View = MinecraftClient.getInstance().options.getPerspective().isFirstPerson();
            boolean isTargetBehind = isPlayerBehind(localPlayer, targetPlayer);

            // Cancel rendering if the local player cannot see the target player or is in F5 mode and the target player is behind
            if (!canSeeTarget || (!isF5View && !isTargetBehind)) {
                if (!localPlayer.isSpectator()) {
                    ci.cancel();
                }
            }
        }
    }

// Method to check if the local player can see the target player
    private boolean isPlayerVisible(PlayerEntity localPlayer, PlayerEntity targetPlayer) {
        // Get the world and eye position of the local player
        MinecraftClient client = MinecraftClient.getInstance();
        net.minecraft.world.World world = client.world;
        Vec3d eyePos = localPlayer.getCameraPosVec(1.0F);

        // Calculate the direction from local player to target player
        Vec3d directionToTarget = targetPlayer.getCameraPosVec(1.0F).subtract(eyePos);

        // Perform raycast to check for entities in the line of sight
        HitResult hitResult = world.raycast(new RaycastContext(eyePos, targetPlayer.getPos(),RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, localPlayer));

        // Check if the raycast hit an entity and it is the target player
        if (hitResult.getType() == HitResult.Type.ENTITY) {
            EntityHitResult entityHit = (EntityHitResult) hitResult;
            if (entityHit.getEntity() == targetPlayer) {
                return true; // The target player is visible
            }
        }

        // Perform raycast to check for blocks in the line of sight near the target player
        double maxDistance = 5.0; // Set the distance to check near the target player in blocks
        Vec3d start = targetPlayer.getCameraPosVec(1.0F).add(0, targetPlayer.getHeight() / 2.0, 0);
        Vec3d end = eyePos.add(directionToTarget.normalize().multiply(maxDistance));
        BlockHitResult blockHitResult = world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, localPlayer));
        BlockState blockState = world.getBlockState(blockHitResult.getBlockPos());
        boolean isGlass = (blockState.getBlock() instanceof GlassBlock || blockState.getBlock() instanceof StainedGlassBlock);
        // Check if the raycast hit a block
        if (blockHitResult.getType() == HitResult.Type.BLOCK && !isGlass) {
            return false; // The target player is not visible
        }

        return true; // The target player is visible
    }

    private boolean isPlayerBehind(PlayerEntity localPlayer, PlayerEntity targetPlayer) {
        // Get the direction the local player is facing
        Direction localPlayerDirection = localPlayer.getHorizontalFacing();

        // Get the vector from local player to target player
        Vec3d localPlayerToTarget = targetPlayer.getCameraPosVec(1.0F).subtract(localPlayer.getCameraPosVec(1.0F)).normalize();

        // Calculate the dot product between the local player's direction and the vector to the target player
        double dotProduct = localPlayerDirection.getOffsetX() * localPlayerToTarget.x + localPlayerDirection.getOffsetZ() * localPlayerToTarget.z;

        // If the dot product is positive, the target player is behind the local player
        return dotProduct > 0;
    }
}
