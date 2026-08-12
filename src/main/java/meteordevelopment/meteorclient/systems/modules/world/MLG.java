/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.world;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class MLG extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> fallDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("fall-distance")
        .description("The fall distance at which to place water.")
        .defaultValue(3)
        .min(0)
        .build()
    );

    private final Setting<Boolean> switchBack = sgGeneral.add(new BoolSetting.Builder()
        .name("switch-back")
        .description("Switches back to the previously selected slot after placing.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> switchBackDelay = sgGeneral.add(new IntSetting.Builder()
        .name("switch-back-delay")
        .description("How many ticks to wait before switching back.")
        .defaultValue(4)
        .min(0)
        .sliderMax(20)
        .visible(switchBack::get)
        .build()
    );

    private boolean attempting;
    private int prevSlot;
    private int switchBackLeft;

    public MLG() {
        super(Categories.World, "mlg", "Automatically places water under you when falling.");
    }

    @Override
    public void onDeactivate() {
        attempting = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player.isCreative() || mc.player.isSpectator()) return;
        if (mc.player.onGround() || mc.player.isInWater()) {
            attempting = false;
            return;
        }

        if (switchBackLeft > 0) {
            switchBackLeft--;

            if (switchBackLeft == 0) {
                InvUtils.swapBack();
            }
        }

        if (attempting || mc.player.getDeltaMovement().y >= -0.5) return;
        if (mc.player.fallDistance < fallDistance.get()) return;

        FindItemResult result = InvUtils.findInHotbar(Items.WATER_BUCKET);
        if (!result.found()) return;

        prevSlot = mc.player.getInventory().getSelectedSlot();
        InvUtils.swap(result.slot(), false);

        attempting = true;
        Rotations.rotate(mc.player.getYRot(), 90, 100, () -> {
            if (!isActive() || mc.player == null) return;

            Vec3 pos = Vec3.atCenterOf(mc.player.blockPosition().below());
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(pos, Direction.UP, mc.player.blockPosition().below(), false));
            mc.player.swing(InteractionHand.MAIN_HAND);

            if (switchBack.get()) {
                switchBackLeft = switchBackDelay.get();
            } else {
                InvUtils.swapBack();
            }

            attempting = false;
        });
    }
}
