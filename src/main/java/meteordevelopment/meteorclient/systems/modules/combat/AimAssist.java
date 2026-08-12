/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.Target;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;

import java.util.Set;

public class AimAssist extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Entities to aim at.")
        .onlyAttackable()
        .defaultValue(EntityTypes.PLAYER)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far away the entity can be for you to aim at it.")
        .defaultValue(4.0)
        .min(0)
        .sliderMax(8)
        .build()
    );

    private final Setting<Double> fov = sgGeneral.add(new DoubleSetting.Builder()
        .name("fov")
        .description("How far from your crosshair the entity can be for you to aim at it.")
        .defaultValue(45)
        .min(0)
        .sliderMax(180)
        .build()
    );

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("How many degrees your look direction can move per tick.")
        .defaultValue(3.0)
        .min(0.1)
        .sliderMax(30)
        .build()
    );

    private final Setting<Boolean> vertical = sgGeneral.add(new BoolSetting.Builder()
        .name("vertical")
        .description("Whether to also aim vertically.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> random = sgGeneral.add(new BoolSetting.Builder()
        .name("randomize")
        .description("Randomizes the turn speed slightly to look more human.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> onClick = sgGeneral.add(new BoolSetting.Builder()
        .name("only-on-click")
        .description("Only aims while you are holding the attack button.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> visibleOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("only-visible")
        .description("Only aims at entities you can see.")
        .defaultValue(true)
        .build()
    );

    public AimAssist() {
        super(Categories.Combat, "aim-assist", "Smoothly moves your crosshair toward nearby entities while attacking.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!Utils.canUpdate() || mc.gui.screen() != null) return;
        if (onClick.get() && !mc.options.keyAttack.isDown()) return;

        Entity target = null;
        double best = range.get();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player || !entity.isAlive() || !EntityUtils.isAttackable(entity.getType())
                || !entities.get().contains(entity.getType())) continue;

            double dist = mc.player.distanceTo(entity);
            if (dist > best) continue;
            if (visibleOnly.get() && !PlayerUtils.canSeeEntity(entity)) continue;

            float yawDiff = Math.abs(Mth.wrapDegrees((float) Rotations.getYaw(entity) - mc.player.getYRot()));
            if (yawDiff > fov.get()) continue;

            best = dist;
            target = entity;
        }

        if (target == null) return;

        float targetYaw = (float) Rotations.getYaw(target);
        float targetPitch = (float) Rotations.getPitch(target, Target.Body);

        float yawDiff = Mth.wrapDegrees(targetYaw - mc.player.getYRot());
        float pitchDiff = Mth.wrapDegrees(targetPitch - mc.player.getXRot());

        double turnSpeed = speed.get();
        if (random.get()) turnSpeed *= 0.7 + Math.random() * 0.6;

        float maxYaw = (float) turnSpeed;
        float maxPitch = (float) (turnSpeed * 0.6);

        if (yawDiff != 0) mc.player.setYRot(mc.player.getYRot() + Mth.clamp(yawDiff, -maxYaw, maxYaw));
        if (vertical.get() && pitchDiff != 0) {
            mc.player.setXRot(Mth.clamp(mc.player.getXRot() + Mth.clamp(pitchDiff, -maxPitch, maxPitch), -90, 90));
        }
    }
}