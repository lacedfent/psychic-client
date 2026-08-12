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

import java.util.ArrayList;
import java.util.List;

public class Blackjack extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> distance = sgGeneral.add(new DoubleSetting.Builder()
        .name("distance")
        .description("How far in front of you the blackjack table spawns.")
        .defaultValue(3.0)
        .min(1)
        .sliderMax(6)
        .build()
    );

    private final Setting<Integer> bet = sgGeneral.add(new IntSetting.Builder()
        .name("bet")
        .description("How many coins you bet per hand.")
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

    private static final double[][] PIPS = new double[11][];

    static {
        PIPS[2] = new double[] { -0.032, 0.125, 0.032, 0.045 };
        PIPS[3] = new double[] { -0.032, 0.125, 0, 0.085, 0.032, 0.045 };
        PIPS[4] = new double[] { -0.032, 0.125, 0.032, 0.125, -0.032, 0.045, 0.032, 0.045 };
        PIPS[5] = new double[] { -0.032, 0.125, 0.032, 0.125, 0, 0.085, -0.032, 0.045, 0.032, 0.045 };
        PIPS[6] = new double[] { -0.032, 0.125, 0, 0.125, 0.032, 0.125, -0.032, 0.045, 0, 0.045, 0.032, 0.045 };
        PIPS[7] = new double[] { -0.032, 0.125, 0, 0.125, 0.032, 0.125, 0, 0.085, -0.032, 0.045, 0, 0.045, 0.032, 0.045 };
        PIPS[8] = new double[] { -0.032, 0.125, 0, 0.125, 0.032, 0.125, -0.032, 0.085, 0.032, 0.085, -0.032, 0.045, 0, 0.045, 0.032, 0.045 };
        PIPS[9] = new double[] { -0.032, 0.125, 0, 0.125, 0.032, 0.125, -0.032, 0.085, 0, 0.085, 0.032, 0.085, -0.032, 0.045, 0, 0.045, 0.032, 0.045 };
        PIPS[10] = new double[]
            { -0.032, 0.145, 0.032, 0.145, -0.032, 0.115, 0.032, 0.115, -0.032, 0.085, 0, 0.085, 0.032, 0.085, -0.032, 0.055, 0, 0.055, 0.032, 0.055 };
    }

    private Machine machine;
    private int balance = 100;
    private State state = State.Idle;

    private final List<int[]> playerCards = new ArrayList<>(); // { rank, suit }
    private final List<int[]> dealerCards = new ArrayList<>();
    private final List<Integer> playerQueue = new ArrayList<>();
    private final List<Integer> dealerQueue = new ArrayList<>();

    private long nextCardAt = -1;
    private long revealAt = -1;
    private boolean turnEnded;

    public Blackjack() {
        super(Categories.Fun, "blackjack", "Spawns a blackjack table only you can see. Draw and stand to beat the dealer!");
    }

    @Override
    public void onActivate() {
        if (!Utils.canUpdate()) return;

        machine = Machine.create(distance.get());
        balance = 100;
        state = State.Idle;

        info("Welcome to the blackjack table! Balance: %d coins!", balance);
    }

    @Override
    public void onDeactivate() {
        machine = null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!Utils.canUpdate()) {
            machine = null;
            return;
        }
        if (machine == null) return;

        long now = System.currentTimeMillis();

        if (nextCardAt < 0 || now < nextCardAt) return;

        if (!playerQueue.isEmpty()) {
            playerCards.add(new int[] { playerQueue.remove(0), (int) (Math.random() * 4) });
            if (sounds.get()) mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1f, 1f);
            if (playerQueue.isEmpty()) {
                info("You now have %s. Total: %d", cardName(playerCards.get(playerCards.size() - 1)), handValue(playerCards, true));
            }
            nextCardAt = now + 300;
            return;
        }

        if (state == State.PlayerTurn && !turnEnded) {
            int pv = handValue(playerCards, true);
            if (pv > 21) {
                turnEnded = true;
                endTurn(true);
                return;
            }
            if (pv == 21) {
                turnEnded = true;
                endTurn(false);
                return;
            }
        }

        if (state == State.DealerTurn) {
            if (revealAt > 0 && now >= revealAt) {
                revealAt = 0;
                if (sounds.get()) mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1f, 1f);
            }

            if (!dealerQueue.isEmpty()) {
                dealerCards.add(new int[] { dealerQueue.remove(0), (int) (Math.random() * 4) });
                if (sounds.get()) mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1f, 1f);
                info("Dealer draws a %s. Total: %d", cardName(dealerCards.get(dealerCards.size() - 1)), handValue(dealerCards, true));
                nextCardAt = now + 900;
                return;
            }

            if (revealAt == 0) {
                revealAt = -1;
                outcome();
            }
        }
    }

    private void startHand() {
        if (balance < bet.get()) {
            error("Not enough coins to bet %d!", bet.get());
            return;
        }

        balance -= bet.get();
        info("New hand! Bet: %d. Balance: %d.", bet.get(), balance);

        playerCards.clear();
        dealerCards.clear();
        playerQueue.clear();
        dealerQueue.clear();

        playerQueue.add(randomRank());
        playerQueue.add(randomRank());
        dealerQueue.add(randomRank());
        dealerCards.add(new int[] { dealerQueue.remove(0), (int) (Math.random() * 4) });
        dealerCards.add(new int[] { randomRank(), (int) (Math.random() * 4) });

        state = State.PlayerTurn;
        turnEnded = false;
        revealAt = -1;
        nextCardAt = System.currentTimeMillis() + 180;
    }

    private void endTurn(boolean busted) {
        state = State.DealerTurn;

        if (busted) {
            revealAt = 1;
        }
        else {
            revealAt = System.currentTimeMillis() + 600;
            List<int[]> sim = new ArrayList<>(dealerCards);
            while (handValue(sim, false) < 17 && sim.size() < 8) {
                sim.add(new int[] { randomRank(), 0 });
            }
            for (int i = dealerCards.size(); i < sim.size(); i++) dealerQueue.add(sim.get(i)[0]);
        }
    }

    private void outcome() {
        state = State.Idle;
        turnEnded = true;

        int pv = handValue(playerCards, true);
        int dv = handValue(dealerCards, true);
        boolean blackjack = pv == 21 && playerCards.size() == 2;

        if (pv > 21) {
            warning("Bust! You lose %d coins. Balance: %d.", bet.get(), balance);
        }
        else if (dv > 21) {
            balance += bet.get() * 2;
            info("Dealer busts with %d! You win %d coins. Balance: %d!", dv, bet.get(), balance);
            if (sounds.get()) mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 1f);
        }
        else if (pv == dv) {
            balance += bet.get();
            info("Push (%d vs %d)! Bet returned. Balance: %d.", pv, dv, balance);
        }
        else if (pv > dv) {
            int win = blackjack ? bet.get() * 3 / 2 : bet.get();
            balance += bet.get() + win;
            if (blackjack) {
                warning("BLACKJACK!!! %d vs %d. You win %d coins. Balance: %d!", pv, dv, win, balance);
            }
            else {
                info("You win! %d vs %d. You win %d coins. Balance: %d!", pv, dv, win, balance);
            }
            if (sounds.get()) mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 1f);
        }
        else {
            info("Dealer wins! %d vs %d. You lose %d coins. Balance: %d.", pv, dv, bet.get(), balance);
        }
    }

    private boolean dealerHidden(int index) {
        if (index != 1 || dealerCards.size() <= 1) return false;
        if (state == State.Idle) return false;
        if (state == State.DealerTurn && revealAt == 0) return false;
        return true;
    }

    private int handValue(List<int[]> cards, boolean includeHidden) {
        int sum = 0;
        int aces = 0;

        for (int i = 0; i < cards.size(); i++) {
            int rank = cards.get(i)[0];
            if (!includeHidden && dealerHidden(i)) continue;

            if (rank == 14) {
                sum += 11;
                aces++;
            }
            else {
                sum += Math.min(rank, 10);
            }
        }

        while (sum > 21 && aces > 0) {
            sum -= 10;
            aces--;
        }

        return sum;
    }

    private int randomRank() {
        return 2 + (int) (Math.random() * 13);
    }

    private String cardName(int[] card) {
        return switch (card[0]) {
            case 11 -> "jack";
            case 12 -> "queen";
            case 13 -> "king";
            case 14 -> "ace";
            default -> String.valueOf(card[0]);
        };
    }

    @EventHandler
    private void onMouseClick(MouseClickEvent event) {
        if (event.action != KeyAction.Press || event.button() != 1) return;
        if (machine == null || !Utils.canUpdate() || mc.gui.screen() != null) return;

        Vec3 origin = mc.player.getEyePosition();
        Vec3 dir = mc.player.getViewVector(1.0f);

        String target = machine.hitTarget(origin, dir, new String[] { "draw", "stand" }, new double[][] {
            { 0.58, 0.66, -0.45, 0.74, 0.80, -0.15 },
            { 0.58, 0.66, 0.15, 0.74, 0.80, 0.45 }
        });
        if (target == null) return;

        if (target.equals("draw")) {
            if (state == State.Idle) startHand();
            else if (state == State.PlayerTurn && playerQueue.isEmpty() && !turnEnded && playerCards.size() < 8) {
                playerQueue.add(randomRank());
                nextCardAt = System.currentTimeMillis() + 300;
            }
        }
        else if (target.equals("stand")) {
            if (state == State.PlayerTurn && playerQueue.isEmpty() && !turnEnded) {
                turnEnded = true;
                endTurn(false);
            }
        }
    }

    @EventHandler
    private void onInteractBlock(InteractBlockEvent event) {
        if (machine == null || !Utils.canUpdate()) return;

        if (machine.hit(mc.player.getEyePosition(), mc.player.getViewVector(1.0f))) event.cancel();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (machine == null) return;

        boolean controls = (state == State.PlayerTurn && playerQueue.isEmpty() && !turnEnded) || state == State.Idle;
        boolean stand = state == State.PlayerTurn && playerQueue.isEmpty() && !turnEnded;
        machine.render(event.renderer, playerCards, dealerCards, dealerHidden(1), controls, stand);
    }

    public enum State {
        Idle,
        PlayerTurn,
        DealerTurn
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
            return new double[] { -0.8, 0.5, -1.2, 0.8, 0.85, 1.2 };
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

        private void pip(Renderer3D renderer, double u, double y0, double vc, double vp, double yp, Color color) {
            box(renderer, u - 0.012, y0 + yp - 0.009, vc + vp - 0.009, u + 0.012, y0 + yp + 0.009, vc + vp + 0.009, color, new Color(0, 0, 0, 255));
        }

        private void pips(Renderer3D renderer, double u, double y0, double vc, int value, Color color) {
            double[] layout = PIPS[value];
            if (layout == null) return;

            for (int i = 0; i < layout.length; i += 2) {
                pip(renderer, u, y0, vc, layout[i], layout[i + 1], color);
            }
        }

        private void suit(Renderer3D renderer, double u, double y0, double vc, int suit, Color color) {
            double y = y0 + 0.045;
            double v = vc;

            switch (suit) {
                case 0 -> { // hearts
                    box(renderer, u - 0.012, y + 0.085, v - 0.016, u + 0.012, y + 0.115, v - 0.004, color, new Color(0, 0, 0, 255));
                    box(renderer, u - 0.012, y + 0.085, v + 0.004, u + 0.012, y + 0.115, v + 0.016, color, new Color(0, 0, 0, 255));
                    box(renderer, u - 0.012, y + 0.045, v - 0.010, u + 0.012, y + 0.075, v + 0.010, color, new Color(0, 0, 0, 255));
                    box(renderer, u - 0.012, y + 0.115, v - 0.003, u + 0.012, y + 0.130, v + 0.003, color, new Color(0, 0, 0, 255));
                }
                case 1 -> { // diamonds
                    box(renderer, u - 0.012, y + 0.065, v - 0.012, u + 0.012, y + 0.105, v + 0.012, color, new Color(0, 0, 0, 255));
                    box(renderer, u - 0.012, y + 0.045, v - 0.004, u + 0.012, y + 0.065, v + 0.004, color, new Color(0, 0, 0, 255));
                    box(renderer, u - 0.012, y + 0.105, v - 0.004, u + 0.012, y + 0.125, v + 0.004, color, new Color(0, 0, 0, 255));
                }
                case 2 -> { // spades
                    box(renderer, u - 0.012, y + 0.085, v - 0.016, u + 0.012, y + 0.115, v - 0.004, color, new Color(0, 0, 0, 255));
                    box(renderer, u - 0.012, y + 0.085, v + 0.004, u + 0.012, y + 0.115, v + 0.016, color, new Color(0, 0, 0, 255));
                    box(renderer, u - 0.012, y + 0.045, v - 0.010, u + 0.012, y + 0.075, v + 0.010, color, new Color(0, 0, 0, 255));
                    box(renderer, u - 0.012, y + 0.115, v - 0.003, u + 0.012, y + 0.128, v + 0.003, color, new Color(0, 0, 0, 255));
                }
                case 3 -> { // clubs
                    box(renderer, u - 0.012, y + 0.075, v - 0.020, u + 0.012, y + 0.095, v - 0.006, color, new Color(0, 0, 0, 255));
                    box(renderer, u - 0.012, y + 0.075, v + 0.006, u + 0.012, y + 0.095, v + 0.020, color, new Color(0, 0, 0, 255));
                    box(renderer, u - 0.012, y + 0.045, v - 0.007, u + 0.012, y + 0.065, v + 0.007, color, new Color(0, 0, 0, 255));
                    box(renderer, u - 0.012, y + 0.095, v - 0.003, u + 0.012, y + 0.125, v + 0.003, color, new Color(0, 0, 0, 255));
                }
            }
        }

        private void card(Renderer3D renderer, double u, double y0, double vc, int[] card, boolean faceUp, boolean highlighted) {
            Color face = new Color(240, 235, 225);
            Color lines = highlighted ? new Color(255, 215, 0) : new Color(140, 130, 110);
            box(renderer, u - 0.012, y0, vc - 0.055, u + 0.012, y0 + 0.16, vc + 0.055, face, lines);

            if (!faceUp) {
                box(renderer, u - 0.010, y0 + 0.075, vc - 0.045, u + 0.010, y0 + 0.085, vc + 0.045, new Color(190, 40, 50), new Color(120, 20, 30));
                box(renderer, u - 0.010, y0 + 0.02, vc - 0.015, u + 0.010, y0 + 0.14, vc + 0.015, new Color(190, 40, 50), new Color(120, 20, 30));
                return;
            }

            int rank = card[0];
            int suit = card[1];
            Color pipColor = (suit == 0 || suit == 1) ? new Color(200, 30, 30) : new Color(25, 25, 30);

            if (rank >= 2 && rank <= 9) {
                pips(renderer, u, y0, vc, rank, pipColor);
            }
            else {
                suit(renderer, u, y0, vc, suit, pipColor);
                if (rank == 14) {
                    box(renderer, u - 0.010, y0 + 0.02, vc - 0.045, u + 0.010, y0 + 0.025, vc + 0.045, new Color(255, 215, 0), new Color(255, 215, 0));
                    box(renderer, u - 0.010, y0 + 0.135, vc - 0.045, u + 0.010, y0 + 0.14, vc + 0.045, new Color(255, 215, 0), new Color(255, 215, 0));
                }
            }
        }

        public void render(Renderer3D renderer, List<int[]> playerCards, List<int[]> dealerCards, boolean hideDealerSecond, boolean controls, boolean stand) {
            box(renderer, -0.75, 0.50, -1.2, 0.75, 0.60, 1.2, new Color(70, 40, 20, 255), new Color(140, 80, 40, 255));
            box(renderer, -0.73, 0.60, -1.18, 0.73, 0.62, 1.18, new Color(10, 110, 50, 255), new Color(0, 80, 30, 255));

            for (int i = 0; i < dealerCards.size(); i++) {
                boolean hidden = hideDealerSecond && i == 1;
                card(renderer, -0.38, 0.64, -0.55 + i * 0.22, dealerCards.get(i), !hidden, true);
            }

            for (int i = 0; i < playerCards.size(); i++) {
                card(renderer, 0.38, 0.64, -0.55 + i * 0.22, playerCards.get(i), true, false);
            }

            if (controls) {
                box(renderer, 0.58, 0.66, -0.45, 0.74, 0.80, -0.15, new Color(255, 205, 60), new Color(180, 140, 20));
                box(renderer, 0.58, 0.66, 0.15, 0.74, 0.80, 0.45, stand ? new Color(220, 40, 40) : new Color(140, 30, 30), new Color(150, 20, 20));
            }
            else {
                box(renderer, 0.58, 0.66, -0.45, 0.74, 0.80, -0.15, new Color(120, 100, 40), new Color(90, 70, 20));
                box(renderer, 0.58, 0.66, 0.15, 0.74, 0.80, 0.45, new Color(90, 30, 30), new Color(70, 20, 20));
            }
        }
    }
}