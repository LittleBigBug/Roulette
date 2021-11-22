package me.matsubara.roulette.runnable;

import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.model.stand.PacketStand;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitRunnable;

public final class MoneyAnimation extends BukkitRunnable {

    private final Game game;
    private final PacketStand moneySlot;

    private boolean goUp;
    private int count;
    private int toFinish;

    public MoneyAnimation(Game game) {
        this.game = game;
        this.moneySlot = game.getModel().getByName("MONEY_SLOT");

        this.goUp = true;
        this.count = 0;
        this.toFinish = 0;
    }

    @Override
    public void run() {
        if (count == 10) {
            count = 0;
            goUp = !goUp;
            toFinish++;
            if (toFinish == 4) {
                game.setMoneyAnimation(null);
                cancel();
            }
        }

        Location to = moneySlot.getLocation().add(0.0d, goUp ? 0.01d : -0.01d, 0.0d);
        moneySlot.teleport(to);
        count++;
    }
}