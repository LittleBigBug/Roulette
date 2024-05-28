package me.matsubara.roulette.game.state;

import com.comphenix.protocol.wrappers.EnumWrappers;
import com.cryptomorin.xseries.XSound;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.event.LastRouletteSpinEvent;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.GameState;
import me.matsubara.roulette.game.data.Bet;
import me.matsubara.roulette.game.data.Slot;
import me.matsubara.roulette.gui.ChipGUI;
import me.matsubara.roulette.gui.ConfirmGUI;
import me.matsubara.roulette.hologram.Hologram;
import me.matsubara.roulette.manager.ConfigManager;
import me.matsubara.roulette.manager.MessageManager;
import me.matsubara.roulette.model.stand.PacketStand;
import me.matsubara.roulette.npc.NPC;
import me.matsubara.roulette.npc.modifier.AnimationModifier;
import me.matsubara.roulette.util.PluginUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class Spinning extends BukkitRunnable {

    private final RoulettePlugin plugin;
    private final Game game;
    private final PacketStand ball;
    private final boolean isEuropean;
    private final Slot[] slots;
    private final int totalTime;

    private int time;
    private boolean shouldStart;

    public Spinning(@NotNull RoulettePlugin plugin, @NotNull Game game) {
        this.plugin = plugin;
        this.game = game;
        this.ball = game.getModel().getByName("BALL");
        this.isEuropean = game.getType().isEuropean();
        this.slots = new Slot[isEuropean ? 37 : 38];
        this.totalTime = ConfigManager.Config.COUNTDOWN_SORTING.asInt() * 20;
        this.time = totalTime;
        this.shouldStart = true;

        System.arraycopy(Slot.values(game), 0, slots, 0, isEuropean ? 37 : 38);

        game.setState(GameState.SPINNING);

        Map<Player, Bet> players = game.getPlayers();
        MessageManager messages = plugin.getMessageManager();

        // Check if the players selected a chip.
        Iterator<Map.Entry<Player, Bet>> iterator = players.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Player, Bet> entry = iterator.next();

            // If somehow player is null (maybe disconnected), continue.
            Player player = entry.getKey();
            if (player == null || !player.isOnline()) continue;

            // If the player didn't select a chip, close inventory and remove from the game.
            Bet bet = entry.getValue();
            if (!bet.hasChip()) {
                game.remove(player, true);
                messages.send(player, MessageManager.Message.OUT_OF_TIME);

                // If the player still has the chip inventory open, close it.
                InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
                if (holder instanceof ChipGUI || holder instanceof ConfirmGUI) {
                    player.closeInventory();
                }

                iterator.remove();
                continue;
            }

            // Show the bet to the player.
            Slot selected = bet.getSlot();
            String numbers = selected.isDoubleZero() ? "[00]" : Arrays.toString(selected.getInts());

            messages.send(player, MessageManager.Message.YOUR_BET, message -> message
                    .replace("%bet%", PluginUtils.getSlotName(selected))
                    .replace("%numbers%", numbers)
                    .replace("%chance%", selected.getChance(game.getType().isEuropean())));
        }

        if (players.isEmpty()) {
            game.setState(GameState.ENDING);
            game.restartRunnable();
            shouldStart = false;
            return;
        }

        NPC npc = game.getNpc();

        game.broadcast(messages.getRandomNPCMessage(npc, "no-bets"));
        game.broadcast(MessageManager.Message.SPINNING_START.asString());

        // Hide holograms to the players so everyone can see the spinning hologram.
        players.forEach((player, bet) -> bet.getHologram().hideTo(player));

        // Play NPC spin animation.
        npc.metadata().queue(PluginUtils.SNEAKING_METADATA, true).send();
        npc.animation().queue(AnimationModifier.EntityAnimation.SWING_MAIN_ARM).send();
        npc.equipment().queue(EnumWrappers.ItemSlot.MAINHAND, new ItemStack(Material.AIR)).send();

        // Show ball, shouldn't be null.
        if (ball != null) ball.setEquipment(new ItemStack(Material.END_ROD), PacketStand.ItemSlot.HEAD);
    }

    @Override
    public void run() {
        if (!shouldStart) {
            cancel();
            return;
        }

        Hologram spinHologram = game.getSpinHologram();

        if (time == 0) {
            spinHologram.setLine(0, ConfigManager.Config.WINNING_NUMBER.asString());

            // Stop NPC animation, check if there are winners and stop.
            game.getNpc().metadata().queue(PluginUtils.SNEAKING_METADATA, false).send();
            game.setState(GameState.ENDING);
            game.checkWinner();

            cancel();
            return;
        }

        // Spin ball.
        Location location = ball.getLocation();
        location.setYaw(location.getYaw() + (time >= totalTime / 3 ? 30.0f : (30.0f * time / totalTime)));
        ball.teleport(location);

        // Select a random number.
        int which = ThreadLocalRandom.current().nextInt(0, isEuropean ? 37 : 38);
        game.setWinner(slots[which]);

        if (time == 1) {
            LastRouletteSpinEvent lastSpinEvent = new LastRouletteSpinEvent(game, game.getWinner());
            plugin.getServer().getPluginManager().callEvent(lastSpinEvent);

            game.setWinner(lastSpinEvent.getWinnerSlot());
        }

        String slotName = PluginUtils.getSlotName(game.getWinner());

        // If the spin hologram is empty, create the lines, else update them.
        if (spinHologram.size() == 0) {

            // Teleport spin hologram to its proper location.
            spinHologram.teleport(ball
                    .getLocation()
                    .clone()
                    .add(0.0d, 2.5d, 0.0d));

            spinHologram.addLines(ConfigManager.Config.SPINNING.asString());
            spinHologram.addLines(slotName);
        } else {
            spinHologram.setLine(1, slotName);

            // Play spinning sound at spin hologram location, this sound can be heard by every player (even those outside the game).
            XSound.play(spinHologram.getLocation(), ConfigManager.Config.SOUND_SPINNING.asString());
        }

        time--;
    }
}