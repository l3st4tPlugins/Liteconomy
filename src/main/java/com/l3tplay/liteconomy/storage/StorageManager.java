package com.l3tplay.liteconomy.storage;

import com.l3tplay.liteconomy.Liteconomy;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public abstract class StorageManager {

    private final Liteconomy plugin;
    private final Map<Player, BigDecimal> playerMap = new HashMap<>();

    private Map<UUID, BigDecimal> baltop = new LinkedHashMap<>();

    public void loadPlayer(Player player) {
        plugin.newChain().asyncFirst(() ->
                loadPlayerData(player, plugin.getStartingValue())).syncLast(balance ->
                    playerMap.put(player, balance.setScale(2, RoundingMode.HALF_EVEN)))
                        .execute();
    }

    public void createAccount(OfflinePlayer player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> loadPlayerData(player, plugin.getStartingValue()));
    }

    public CompletableFuture<BigDecimal> getBalance(OfflinePlayer player) {
        if (player.isOnline() && playerMap.containsKey(player.getPlayer())) {
            return CompletableFuture.completedFuture(playerMap.get(player.getPlayer()));
        }

        return CompletableFuture.supplyAsync(() -> loadPlayerData(player, plugin.getStartingValue()));
    }

    public boolean setBalance(OfflinePlayer player, BigDecimal money) {
        if (!player.hasPlayedBefore()) {
            return false;
        }

        if (money.signum() == -1) {
            return false;
        }

        if (player.isOnline() && !playerMap.containsKey(player.getPlayer())) {
            return false;
        }

        BigDecimal scaledMoney = money.setScale(2, RoundingMode.HALF_EVEN);

        if (player.isOnline()) {
            playerMap.put(player.getPlayer(), scaledMoney);
        }else{
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> savePlayerData(player, scaledMoney));
        }

        return true;
    }

    public boolean addBalance(OfflinePlayer player, double money) {
        return setBalance(player, getBalance(player).join().add(BigDecimal.valueOf(money)));
    }

    public boolean removeBalance(OfflinePlayer player, double money) {
        return setBalance(player, getBalance(player).join().subtract(BigDecimal.valueOf(money)));
    }

    public void savePlayer(Player player) {
        BigDecimal balance = playerMap.remove(player);
        savePlayerData(player, balance);
    }

    public void savePlayerAsync(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> savePlayer(player));
    }

    public void updateBaltop() {
        plugin.newChain().asyncFirst(() -> sortPlayers())
                .syncLast((sortedMap) -> {
                   for (Player player : Bukkit.getOnlinePlayers()) {
                       sortedMap.put(player.getUniqueId(), getBalance(player).join());
                   }
                   this.baltop = sortedMap
                           .entrySet()
                           .stream()
                           .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                           .limit(plugin.getConfig().getInt("settings.baltopLimit"))
                           .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2,
                                           LinkedHashMap::new));
                }).execute();
    }

    public void autosave() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                savePlayerData(player, getBalance(player).join());
            }
        });
    }

    public boolean isAccountCreated(OfflinePlayer player) {
        if (player.isOnline()) {
            return true;
        }

        return hasAccount(player);
    }

    public Map<UUID, BigDecimal> getBaltop() {
        return Collections.unmodifiableMap(baltop);
    }

    public abstract void init();
    public abstract void close();

    protected abstract BigDecimal loadPlayerData(OfflinePlayer player, BigDecimal defaultValue);
    protected abstract void savePlayerData(OfflinePlayer player, BigDecimal balance);
    protected abstract boolean hasAccount(OfflinePlayer player);
    protected abstract Map<UUID, BigDecimal> sortPlayers();
}
