package com.hollingsworth.arsnouveau.client;

import com.hollingsworth.arsnouveau.ArsNouveau;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import net.minecraft.client.Camera;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;

import static com.hollingsworth.arsnouveau.client.ClientInfo.skyRenderTarget;
import static org.lwjgl.opengl.GL11C.GL_SCISSOR_TEST;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = ArsNouveau.MODID)
public class SkyTextureHandler {

    @SubscribeEvent
    public static void renderSky(RenderLevelStageEvent event) {
        if (event.getStage().equals(RenderLevelStageEvent.Stage.AFTER_SKY)) {
            Minecraft minecraft = Minecraft.getInstance();
            if (skyRenderTarget == null) {
                Window window = minecraft.getWindow();
                setupRenderTarget(window.getWidth(), window.getHeight());
            }
            PoseStack poseStack = event.getPoseStack();
            GameRenderer gameRenderer = minecraft.gameRenderer;
            LevelRenderer levelRenderer = minecraft.levelRenderer;
            Camera camera = gameRenderer.getMainCamera();
            Vec3 cameraPosition = camera.getPosition();
            Matrix4f projectionMatrix = event.getProjectionMatrix();

            float partialTick = event.getPartialTick();
            boolean isFoggy = minecraft.level.effects().isFoggyAt(Mth.floor(cameraPosition.x), Mth.floor(cameraPosition.y)) || minecraft.gui.getBossOverlay().shouldCreateWorldFog();

            //setting the render target to our sky target
            skyRenderTarget.bindWrite(true);
            //clearing what was rendered the previous frame
            RenderSystem.clear(16640, Minecraft.ON_OSX);

            FogRenderer.setupColor(camera, partialTick, minecraft.level, minecraft.options.getEffectiveRenderDistance(), gameRenderer.getDarkenWorldAmount(partialTick));
            FogRenderer.levelFogColor();
            //rendering the actual sky
            RenderSystem.setShader(GameRenderer::getPositionShader);
            levelRenderer.renderSky(poseStack, projectionMatrix, partialTick, camera, isFoggy, () -> {
                FogRenderer.setupFog(camera, FogRenderer.FogMode.FOG_SKY, gameRenderer.getRenderDistance(), isFoggy, partialTick);
            });

            PoseStack modelViewStack = RenderSystem.getModelViewStack();
            modelViewStack.pushPose();
            modelViewStack.mulPoseMatrix(poseStack.last().pose());
            RenderSystem.applyModelViewMatrix();

            //rendering the clouds
            if (minecraft.options.getCloudsType() != CloudStatus.OFF) {
                RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
                levelRenderer.renderClouds(poseStack, projectionMatrix, partialTick, cameraPosition.x, cameraPosition.y, cameraPosition.z);
            }

            //the rain!
            RenderSystem.depthMask(false);
            levelRenderer.renderSnowAndRain(gameRenderer.lightTexture(), partialTick, cameraPosition.x, cameraPosition.y, cameraPosition.z);
            RenderSystem.depthMask(true);

            modelViewStack.popPose();
            RenderSystem.applyModelViewMatrix();
            minecraft.getMainRenderTarget().bindWrite(true);
        }
    }

    public static void setupRenderTarget(int width, int height) {
        final Minecraft instance = Minecraft.getInstance();
        skyRenderTarget = new TextureTarget(width, height, true, Minecraft.ON_OSX);
    }
}
