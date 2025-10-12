package com.nnpg.glazed.modules.movement;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.events.meteor.MouseButtonEvent;
import meteordevelopment.meteorclient.events.meteor.MouseScrollEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.ChunkOcclusionEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.GUIMove;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.DeathMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.lwjgl.glfw.GLFW;

/**
 * Freecam module – allows camera movement independent of the player.
 */
public class Freecam extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Your speed while in freecam.")
        .onChanged(aDouble -> speedValue = aDouble)
        .defaultValue(1.0)
        .min(0.0)
        .build()
    );

    private final Setting<Double> speedScrollSensitivity = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed-scroll-sensitivity")
        .description("Change speed using scroll wheel. 0 disables this feature.")
        .defaultValue(0.0)
        .min(0.0)
        .sliderMax(2.0)
        .build()
    );

    private final Setting<Boolean> staySneaking = sgGeneral.add(new BoolSetting.Builder()
        .name("stay-sneaking")
        .description("Keep player sneaking while in freecam.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> toggleOnDamage = sgGeneral.add(new BoolSetting.Builder()
        .name("toggle-on-damage")
        .description("Disables freecam when you take damage.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> toggleOnDeath = sgGeneral.add(new BoolSetting.Builder()
        .name("toggle-on-death")
        .description("Disables freecam when you die.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> toggleOnLog = sgGeneral.add(new BoolSetting.Builder()
        .name("toggle-on-log")
        .description("Disables freecam when you disconnect.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> reloadChunks = sgGeneral.add(new BoolSetting.Builder()
        .name("reload-chunks")
        .description("Reloads chunks to disable cave culling.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> renderHands = sgGeneral.add(new BoolSetting.Builder()
        .name("show-hands")
        .description("Render hands in freecam.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotates to the block or entity you are looking at.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> staticView = sgGeneral.add(new BoolSetting.Builder()
        .name("static-view")
        .description("Disable FOV and bobbing effects while in freecam.")
        .defaultValue(true)
        .build()
    );

    public final Vector3d pos = new Vector3d();
    public final Vector3d prevPos = new Vector3d();

    private Perspective perspective;
    private double speedValue;

    public float yaw, pitch;
    public float lastYaw, lastPitch;

    private double fovScale;
    private boolean bobView;

    private boolean forward, backward, right, left, up, down, isSneaking;

    public Freecam() {
        // Make sure GlazedAddon defines: public static final Category movement = new Category("Movement");
        super(GlazedAddon.movement, "freecam", "Allows the camera to move away from the player and mine blocks.");
    }

    @Override
    public void onActivate() {
        fovScale = mc.options.getFovEffectScale().getValue();
        bobView = mc.options.getBobView().getValue();

        if (staticView.get()) {
            mc.options.getFovEffectScale().setValue(0.0);
            mc.options.getBobView().setValue(false);
        }

        yaw = mc.player.getYaw();
        pitch = mc.player.getPitch();
        perspective = mc.options.getPerspective();
        speedValue = speed.get();

        Utils.set(pos, mc.gameRenderer.getCamera().getPos());
        Utils.set(prevPos, mc.gameRenderer.getCamera().getPos());

        if (mc.options.getPerspective() == Perspective.THIRD_PERSON_FRONT) {
            yaw += 180;
            pitch *= -1;
        }

        lastYaw = yaw;
        lastPitch = pitch;

        isSneaking = mc.options.sneakKey.isPressed();
        forward = Input.isPressed(mc.options.forwardKey);
        backward = Input.isPressed(mc.options.backKey);
        right = Input.isPressed(mc.options.rightKey);
        left = Input.isPressed(mc.options.leftKey);
        up = Input.isPressed(mc.options.jumpKey);
        down = Input.isPressed(mc.options.sneakKey);

        unpressKeys();
        if (reloadChunks.get()) mc.worldRenderer.reload();
    }

    @Override
    public void onDeactivate() {
        if (reloadChunks.get()) mc.execute(mc.worldRenderer::reload);
        mc.options.setPerspective(perspective);

        if (staticView.get()) {
            mc.options.getFovEffectScale().setValue(fovScale);
            mc.options.getBobView().setValue(bobView);
        }

        isSneaking = false;
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        unpressKeys();
        prevPos.set(pos);
        lastYaw = yaw;
        lastPitch = pitch;
    }

    private void unpressKeys() {
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.cameraEntity.isInsideWall()) mc.getCameraEntity().noClip = true;
        if (mc.options.getPerspective() != Perspective.FIRST_PERSON) mc.options.setPerspective(Perspective.FIRST_PERSON);

        Vec3d forwardVec = Vec3d.fromPolar(0, yaw);
        Vec3d rightVec = Vec3d.fromPolar(0, yaw + 90);
        double velX = 0, velY = 0, velZ = 0;

        if (forward) { velX += forwardVec.x * speedValue; velZ += forwardVec.z * speedValue; }
        if (backward) { velX -= forwardVec.x * speedValue; velZ -= forwardVec.z * speedValue; }
        if (right) { velX += rightVec.x * speedValue; velZ += rightVec.z * speedValue; }
        if (left) { velX -= rightVec.x * speedValue; velZ -= rightVec.z * speedValue; }
        if (up) velY += speedValue;
        if (down) velY -= speedValue;

        if ((forward || backward) && (right || left)) {
            double diagonal = 1 / Math.sqrt(2);
            velX *= diagonal;
            velZ *= diagonal;
        }

        prevPos.set(pos);
        pos.add(velX, velY, velZ);

        if (mc.crosshairTarget instanceof BlockHitResult blockTarget && Input.isPressed(mc.options.attackKey)) {
            mc.interactionManager.attackBlock(blockTarget.getBlockPos(), blockTarget.getSide());
        }

        if (rotate.get()) {
            if (mc.crosshairTarget instanceof EntityHitResult ehr) {
                BlockPos bp = ehr.getEntity().getBlockPos();
                Rotations.rotate(Rotations.getYaw(bp), Rotations.getPitch(bp), 0, null);
            } else if (mc.crosshairTarget instanceof BlockHitResult bhr) {
                Vec3d pos = bhr.getPos();
                BlockPos bp = bhr.getBlockPos();
                if (!mc.world.getBlockState(bp).isAir()) {
                    Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos), 0, null);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onKey(KeyEvent event) {
        if (Input.isKeyPressed(GLFW.GLFW_KEY_F3) || checkGuiMove()) return;
        boolean cancel = true;

        if (mc.options.forwardKey.matchesKey(event.key, 0)) forward = event.action != KeyAction.Release;
        else if (mc.options.backKey.matchesKey(event.key, 0)) backward = event.action != KeyAction.Release;
        else if (mc.options.rightKey.matchesKey(event.key, 0)) right = event.action != KeyAction.Release;
        else if (mc.options.leftKey.matchesKey(event.key, 0)) left = event.action != KeyAction.Release;
        else if (mc.options.jumpKey.matchesKey(event.key, 0)) up = event.action != KeyAction.Release;
        else if (mc.options.sneakKey.matchesKey(event.key, 0)) down = event.action != KeyAction.Release;
        else cancel = false;

        if (cancel) event.cancel();
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onMouseButton(MouseButtonEvent event) {
        if (checkGuiMove()) return;
        boolean cancel = true;

        if (mc.options.forwardKey.matchesMouse(event.button)) forward = event.action != KeyAction.Release;
        else if (mc.options.backKey.matchesMouse(event.button)) backward = event.action != KeyAction.Release;
        else if (mc.options.rightKey.matchesMouse(event.button)) right = event.action != KeyAction.Release;
        else if (mc.options.leftKey.matchesMouse(event.button)) left = event.action != KeyAction.Release;
        else if (mc.options.jumpKey.matchesMouse(event.button)) up = event.action != KeyAction.Release;
        else if (mc.options.sneakKey.matchesMouse(event.button)) down = event.action != KeyAction.Release;
        else cancel = false;

        if (cancel) event.cancel();
    }

    @EventHandler(priority = EventPriority.LOW)
    private void onMouseScroll(MouseScrollEvent event) {
        if (speedScrollSensitivity.get() > 0.0 && mc.currentScreen == null) {
            speedValue += event.value * 0.25 * (speedScrollSensitivity.get() * speedValue);
            if (speedValue < 0.1) speedValue = 0.1;
            event.cancel();
        }
    }

    @EventHandler
    private void onChunkOcclusion(ChunkOcclusionEvent event) {
        event.cancel();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (toggleOnLog.get()) toggle();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof DeathMessageS2CPacket packet) {
            Entity entity = mc.world.getEntityById(packet.playerId());
            if (entity == mc.player && toggleOnDeath.get()) {
                toggle();
                info("Toggled off because you died.");
            }
        } else if (event.packet instanceof HealthUpdateS2CPacket packet) {
            if (mc.player.getHealth() - packet.getHealth() > 0 && toggleOnDamage.get()) {
                toggle();
                info("Toggled off because you took damage.");
            }
        }
    }

    private boolean checkGuiMove() {
        GUIMove guiMove = Modules.get().get(GUIMove.class);
        return mc.currentScreen != null && (!guiMove.isActive() || guiMove.skip());
    }

    public void changeLookDirection(double deltaX, double deltaY) {
        lastYaw = yaw;
        lastPitch = pitch;
        yaw += (float) deltaX;
        pitch += (float) deltaY;
        pitch = MathHelper.clamp(pitch, -90, 90);
    }

    public boolean renderHands() { return !isActive() || renderHands.get(); }
    public boolean staySneaking() { return isActive() && !mc.player.getAbilities().flying && staySneaking.get() && isSneaking; }

    public double getX(float tickDelta) { return MathHelper.lerp(tickDelta, prevPos.x, pos.x); }
    public double getY(float tickDelta) { return MathHelper.lerp(tickDelta, prevPos.y, pos.y); }
    public double getZ(float tickDelta) { return MathHelper.lerp(tickDelta, prevPos.z, pos.z); }
    public double getYaw(float tickDelta) { return MathHelper.lerp(tickDelta, lastYaw, yaw); }
    public double getPitch(float tickDelta) { return MathHelper.lerp(tickDelta, lastPitch, pitch); }
}
