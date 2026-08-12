/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.meteor.MouseClickEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.Renderer3D;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

public class Roulette extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> distance = sgGeneral.add(new DoubleSetting.Builder()
        .name("distance")
        .description("How far in front of you the roulette table spawns.")
        .defaultValue(3.0)
        .min(1)
        .sliderMax(6)
        .build()
    );

    private final Setting<Integer> bet = sgGeneral.add(new IntSetting.Builder()
        .name("bet")
        .description("How many coins you bet per spin.")
        .defaultValue(10)
        .min(1)
        .sliderMax(100)
        .build()
    );

    private final Setting<Boolean> sounds = sgGeneral.add(new BoolSetting.Builder()
        .name("sounds")
        .description("Whether to play sounds when playing.")
        .defaultValue(true)
        .build()
    );

    private static final int SPIN_TIME = 4000;
    private static final int POCKETS = 13;
    private static final int[] RED_POCKETS = { 1, 3, 5, 7, 9, 11 };
    private static final int[] BLACK_POCKETS = { 2, 4, 6, 8, 10, 12 };

    private Machine machine;
    private int balance = 100;
    private Bet selected = Bet.RED;

    private long spinStart = -1;
    private long spinEnd;
    private double startAngle;
    private double landAngle;
    private double totalDeg;
    private int landingPocket;
    private long wonAt = -1;

    public Roulette() {
        super(Categories.Fun, "roulette", "Spawns a roulette table only you can see. Pick a bet and spin the wheel!");
    }

    @Override
    public void onActivate() {
        if (!Utils.canUpdate()) return;

        machine = Machine.create(distance.get());
        balance = 100;
        wonAt = -1;

        info("The roulette table appears... balance: %d coins!", balance);
    }

    @Override
    public void onDeactivate() {
        machine = null;
        spinStart = -1;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!Utils.canUpdate()) {
            machine = null;
            return;
        }
        if (machine == null || spinStart < 0 || wonAt >= 0) return;

        long now = System.currentTimeMillis();
        if (now < spinEnd) return;
        wonAt = now;

        boolean win = pocketMatches(selected, landingPocket);
        if (win) {
            balance += bet.get() * 2;
            info("Ball lands on %d %s! You win %d coins. Balance: %d!", landingPocket, pocketColorName(landingPocket), bet.get() * 2, balance);
            if (sounds.get()) mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 1f);
        }
        else {
            info("Ball lands on %d %s... you lose. Balance: %d.", landingPocket, pocketColorName(landingPocket), balance);
        }
    }

    @EventHandler
    private void onMouseClick(MouseClickEvent event) {
        if (event.action != KeyAction.Press || event.button() != 1) return;
        if (machine == null || !Utils.canUpdate() || mc.gui.screen() != null) return;

        Vec3 origin = mc.player.getEyePosition();
        Vec3 dir = mc.player.getViewVector(1.0f);

        String target = machine.hitTarget(origin, dir, new String[] { "red", "black", "odd", "even", "spin" }, new double[][] {
            { 0.30, 0.62, -0.80, 0.46, 0.68, -0.60 },
            { 0.30, 0.62, -0.30, 0.46, 0.68, -0.10 },
            { 0.30, 0.62, 0.10, 0.46, 0.68, 0.30 },
            { 0.30, 0.62, 0.60, 0.46, 0.68, 0.80 },
            { 0.30, 0.62, 0.85, 0.46, 0.72, 1.05 }
        });
        if (target == null) return;

        if (target.equals("red") || target.equals("black") || target.equals("odd") || target.equals("even")) {
            selected = Bet.valueOf(target.toUpperCase());
            if (sounds.get()) mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1f, 1f);
        }
        else if (target.equals("spin")) {
            spin();
        }
    }

    @EventHandler
    private void onInteractBlock(InteractBlockEvent event) {
        if (machine == null || !Utils.canUpdate()) return;

        if (machine.hit(mc.player.getEyePosition(), mc.player.getViewVector(1.0f))) event.cancel();
    }

    private void spin() {
        long now = System.currentTimeMillis();
        if (spinStart >= 0 && now < spinEnd) return;
        if (balance < bet.get()) {
            error("Not enough coins to bet %d!", bet.get());
            return;
        }

        balance -= bet.get();

        int[] pool = switch (selected) {
            case RED, ODD -> RED_POCKETS;
            case BLACK, EVEN -> BLACK_POCKETS;
        };
        landingPocket = pool[(int) (Math.random() * pool.length)];
        landAngle = pocketAngle(landingPocket);

        double current = spinStart >= 0 ? ballAngle(now) : landAngle;
        startAngle = current;
        totalDeg = 1080 + ((startAngle - landAngle) % 360 + 360) % 360;

        spinStart = now;
        spinEnd = now + SPIN_TIME;
        wonAt = -1;

        if (sounds.get()) mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1f, 1f);
    }

    private boolean pocketMatches(Bet bet, int pocket) {
        return switch (bet) {
            case RED -> pocket % 2 == 1 && pocket != 0;
            case BLACK -> pocket % 2 == 0 && pocket != 0;
            case ODD -> pocket % 2 == 1;
            case EVEN -> pocket % 2 == 0;
        };
    }

    private String pocketColorName(int pocket) {
        if (pocket == 0) return "green 0";
        return pocket % 2 == 1 ? "red" : "black";
    }

    private boolean isSpinning(long now) {
        return spinStart >= 0 && now < spinEnd;
    }

    private double ballAngle(long now) {
        if (spinStart < 0 || now >= spinEnd) return landAngle;

        double p = Math.min(1.0, (now - spinStart) / (double) SPIN_TIME);
        double f = 1 - Math.pow(1 - p, 3);
        return wrapDeg(startAngle - totalDeg * f);
    }

    private static double wrapDeg(double a) {
        return ((a % 360) + 360) % 360;
    }

    private static double pocketAngle(int pocket) {
        return 90.0 - pocket * (360.0 / POCKETS);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (machine == null) return;

        long now = System.currentTimeMillis();
        machine.render(event.renderer, now, ballAngle(now), isSpinning(now), now - wonAt >= 0 && now - wonAt < 2500, landingPocket, selected);
    }

    public enum Bet {
        RED,
        BLACK,
        ODD,
        EVEN
    }

    private static class Machine {
        private final Vec3 center;
        private final Vec3 front;
        private final Vec3 right;

        private Machine(BlockPos base, Vec3 front) {
            this.center = new Vec3(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
            this.front = front;
            this.right = new Vec3(-front.z, 0, front.x);
        }

        public static Machine create(double distance) {
            BlockPos base = basePos(distance);
            return new Machine(base, frontDir());
        }

        private static double[] bounds() {
            return new double[] { -0.7, 0.5, -1.2, 0.7, 1.75, 1.2 };
        }

        public boolean hit(Vec3 origin, Vec3 dir) {
            return hitBox(origin, dir, bounds()[0], bounds()[1], bounds()[2], bounds()[3], bounds()[4], bounds()[5]) >= 0;
        }

        public String hitTarget(Vec3 origin, Vec3 dir, String[] targets, double[][] targetBoxes) {
            double best = 1e9;
            String result = null;

            for (int i = 0; i < targets.length; i++) {
                double[] b = targetBoxes[i];
                double t = hitBox(origin, dir, b[0], b[1], b[2], b[3], b[4], b[5]);
                if (t >= 0 && t < best) {
                    best = t;
                    result = targets[i];
                }
            }

            return result;
        }

        public Vec3 local(double u, double y, double v) {
            return new Vec3(center.x + front.x * u + right.x * v, center.y + y, center.z + front.z * u + right.z * v);
        }

        public double hitBox(Vec3 origin, Vec3 dir, double u1, double y1, double v1, double u2, double y2, double v2) {
            Vec3 min = local(u1, y1, v1);
            Vec3 max = local(u2, y2, v2);

            double tMin = 0;
            double tMax = 6;

            for (int i = 0; i < 3; i++) {
                double o = i == 0 ? origin.x : i == 1 ? origin.y : origin.z;
                double d = i == 0 ? dir.x : i == 1 ? dir.y : dir.z;
                double lo = i == 0 ? min.x : i == 1 ? min.y : min.z;
                double hi = i == 0 ? max.x : i == 1 ? max.y : max.z;

                if (Math.abs(d) < 1e-8) {
                    if (o < lo || o > hi) return -1;
                }
                else {
                    double t1 = (lo - o) / d;
                    double t2 = (hi - o) / d;
                    if (t1 > t2) {
                        double tmp = t1;
                        t1 = t2;
                        t2 = tmp;
                    }
                    tMin = Math.max(tMin, t1);
                    tMax = Math.min(tMax, t2);
                    if (tMin > tMax) return -1;
                }
            }

            return tMin;
        }

        public static BlockPos basePos(double distance) {
            Vec3 eye = MeteorClient.mc.player.getEyePosition();
            Vec3 look = MeteorClient.mc.player.getViewVector(1.0f);
            BlockPos pos = BlockPos.containing(eye.x + look.x * distance, eye.y + look.y * distance, eye.z + look.z * distance);

            for (int i = 0; i < 8; i++) {
                if (!MeteorClient.mc.level.getBlockState(pos.below()).isAir() || pos.getY() <= MeteorClient.mc.level.getMinY()) break;
                pos = pos.below();
            }

            return pos;
        }

        public static Vec3 frontDir() {
            double fx = -Math.sin(Math.toRadians(MeteorClient.mc.player.getYRot()));
            double fz = -Math.cos(Math.toRadians(MeteorClient.mc.player.getYRot()));
            return Math.abs(fx) > Math.abs(fz) ? new Vec3(Math.signum(fx), 0, 0) : new Vec3(0, 0, Math.signum(fz));
        }

        private void box(Renderer3D renderer, double u1, double y1, double v1, double u2, double y2, double v2, Color side, Color lines) {
            Vec3 min = local(u1, y1, v1);
            Vec3 max = local(u2, y2, v2);
            renderer.box(min.x, min.y, min.z, max.x, max.y, max.z, side, lines, ShapeMode.Both, 0);
        }

        private void chip(Renderer3D renderer, double vc, Color color, boolean selected) {
            box(renderer, 0.30, 0.62, vc - 0.10, 0.46, 0.68, vc + 0.10, color, new Color(0, 0, 0, 255));
            if (selected) {
                box(renderer, 0.28, 0.60, vc - 0.12, 0.30, 0.62, vc + 0.12, new Color(255, 215, 0), new Color(255, 215, 0));
                box(renderer, 0.46, 0.60, vc - 0.12, 0.48, 0.62, vc + 0.12, new Color(255, 215, 0), new Color(255, 215, 0));
                box(renderer, 0.30, 0.60, vc - 0.12, 0.46, 0.62, vc - 0.10, new Color(255, 215, 0), new Color(255, 215, 0));
                box(renderer, 0.30, 0.60, vc + 0.10, 0.46, 0.62, vc + 0.12, new Color(255, 215, 0), new Color(255, 215, 0));
            }
        }

        public void render(Renderer3D renderer, long now, double ballAngle, boolean spinning, boolean celebrating, int winningPocket, Bet selected) {
            box(renderer, -0.65, 0.50, -1.15, 0.65, 0.60, 1.15, new Color(80, 45, 20, 255), new Color(150, 90, 40, 255));
            box(renderer, -0.63, 0.60, -1.13, 0.63, 0.62, 1.13, new Color(10, 110, 50, 255), new Color(0, 80, 30, 255));

            chip(renderer, -0.70, new Color(210, 40, 50), selected == Bet.RED);
            chip(renderer, -0.20, new Color(35, 35, 40), selected == Bet.BLACK);
            chip(renderer, 0.20, new Color(255, 150, 40), selected == Bet.ODD);
            chip(renderer, 0.70, new Color(50, 60, 220), selected == Bet.EVEN);

            box(renderer, 0.30, 0.62, 0.85, 0.46, 0.70, 1.05, new Color(255, 205, 60), new Color(180, 140, 20));
            box(renderer, 0.32, 0.70, 0.87, 0.44, 0.72, 1.03, new Color(220, 40, 40), new Color(150, 20, 20));

            double wheelU = -0.55;
            double wheelY = 1.15;
            double wheelV = -0.2;
            double r = 0.36;

            for (int i = 0; i < POCKETS; i++) {
                double a = Math.toRadians(pocketAngle(i));
                double y = wheelY + Math.cos(a) * r;
                double v = wheelV + Math.sin(a) * r;
                Color color;
                if (i == 0) color = new Color(0, 150, 60);
                else color = i % 2 == 1 ? new Color(200, 30, 40) : new Color(25, 25, 30);

                boolean highlight = celebrating && i == winningPocket;
                box(renderer, wheelU - 0.035, y - 0.05, v - 0.05, wheelU + 0.035, y + 0.05, v + 0.05,
                    highlight ? new Color(255, 215, 0) : color, highlight ? new Color(255, 255, 255) : new Color(0, 0, 0));
            }

            for (int k = 0; k < 16; k++) {
                double a = Math.toRadians(k * (360.0 / 16));
                double y = wheelY + Math.cos(a) * 0.45;
                double v = wheelV + Math.sin(a) * 0.45;
                box(renderer, wheelU - 0.02, y - 0.035, v - 0.035, wheelU + 0.02, y + 0.035, v + 0.035, new Color(60, 30, 110), new Color(30, 10, 60));
            }

            box(renderer, wheelU - 0.03, wheelY - 0.045, wheelV - 0.045, wheelU + 0.03, wheelY + 0.045, wheelV + 0.045, new Color(255, 215, 0), new Color(180, 140, 20));
            box(renderer, wheelU - 0.03, wheelY + 0.415, wheelV - 0.012, wheelU + 0.03, wheelY + 0.475, wheelV + 0.012, new Color(255, 215, 0), new Color(180, 140, 20));

            double a = Math.toRadians(ballAngle);
            double y = wheelY + Math.cos(a) * r;
            double v = wheelV + Math.sin(a) * r;
            box(renderer, wheelU - 0.02, y - 0.03, v - 0.03, wheelU + 0.02, y + 0.03, v + 0.03, new Color(255, 240, 180), new Color(200, 160, 60));
        }
    }
}