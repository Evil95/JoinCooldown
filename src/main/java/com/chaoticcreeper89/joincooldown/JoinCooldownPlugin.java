package com.chaoticcreeper89.joincooldown;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class JoinCooldownPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private final Map<UUID, Long> rejoinTimes = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);

        if (getCommand("joincooldown") != null) {
            getCommand("joincooldown").setExecutor(this);
            getCommand("joincooldown").setTabCompleter(this);
        }

        getLogger().info("JoinCooldownPlugin wurde aktiviert!");
    }

    @Override
    public void onDisable() {
        rejoinTimes.clear();
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        FileConfiguration config = getConfig();

        // Prüfen, ob der Cooldown aktiv ist
        if (!config.getBoolean("Cooldown Enabled", true)) {
            return;
        }

        Player player = event.getPlayer();

        // 1. Prüfung: Hat der Spieler die Bypass-Permission?
        if (player.hasPermission("joincooldown.bypass")) {
            return;
        }

        // 2. Prüfung: Ist der Spieler auf der Whitelist in der config.yml?
        List<String> whitelist = config.getStringList("Whitelist");
        if (whitelist.contains(player.getName()) || whitelist.contains(player.getUniqueId().toString())) {
            return;
        }

        UUID playerId = player.getUniqueId();

        if (rejoinTimes.containsKey(playerId)) {
            long lastQuitTime = rejoinTimes.get(playerId);
            long currentTime = System.currentTimeMillis();

            long cooldownTimeMs = config.getInt("Cooldown MS", 5) * 1000L;
            long timeElapsed = currentTime - lastQuitTime;

            if (timeElapsed < cooldownTimeMs) {
                long timeLeftSeconds = (cooldownTimeMs - timeElapsed) / 1000L;
                if (timeLeftSeconds <= 0) {
                    timeLeftSeconds = 1;
                }

                String message = ChatColor.RED + "Du musst mindestens " + timeLeftSeconds + " Sekunden warten, bevor du erneut beitreten kannst.";
                event.disallow(PlayerLoginEvent.Result.KICK_OTHER, message);
            } else {
                rejoinTimes.remove(playerId);
            }
        }
    }

    @EventHandler
    public void onPlayerLogout(PlayerQuitEvent event) {
        rejoinTimes.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("joincooldown.admin")) {
            sender.sendMessage(ChatColor.RED + "Du hast keine Berechtigung für diesen Befehl.");
            return true;
        }

        if (args.length == 0) {
            boolean currentState = getConfig().getBoolean("Cooldown Enabled", true);
            sender.sendMessage(ChatColor.GOLD + "[JoinCooldown] " + ChatColor.YELLOW + "Der Cooldown ist aktuell " + 
                    (currentState ? ChatColor.GREEN + "AKTIVIERT" : ChatColor.RED + "DEAKTIVIERT") + ChatColor.YELLOW + ".");
            sender.sendMessage(ChatColor.GRAY + "Nutzung: /" + label + " <on|off|toggle|set|whitelist|reload>");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "on":
            case "enable":
                getConfig().set("Cooldown Enabled", true);
                saveConfig();
                sender.sendMessage(ChatColor.GOLD + "[JoinCooldown] " + ChatColor.GREEN + "Der Join-Cooldown wurde aktiviert!");
                break;

            case "off":
            case "disable":
                getConfig().set("Cooldown Enabled", false);
                saveConfig();
                sender.sendMessage(ChatColor.GOLD + "[JoinCooldown] " + ChatColor.RED + "Der Join-Cooldown wurde deaktiviert!");
                break;

            case "toggle":
                boolean newState = !getConfig().getBoolean("Cooldown Enabled", true);
                getConfig().set("Cooldown Enabled", newState);
                saveConfig();
                sender.sendMessage(ChatColor.GOLD + "[JoinCooldown] " + ChatColor.YELLOW + "Der Join-Cooldown ist nun " + 
                        (newState ? ChatColor.GREEN + "AKTIVIERT" : ChatColor.RED + "DEAKTIVIERT") + ChatColor.YELLOW + ".");
                break;

            case "set":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Nutzung: /" + label + " set <Sekunden>");
                    return true;
                }
                try {
                    int seconds = Integer.parseInt(args[1]);
                    if (seconds < 0) {
                        sender.sendMessage(ChatColor.RED + "Die Zeit muss positiv sein!");
                        return true;
                    }
                    getConfig().set("Cooldown MS", seconds);
                    saveConfig();
                    sender.sendMessage(ChatColor.GOLD + "[JoinCooldown] " + ChatColor.GREEN + "Cooldown-Zeit auf " + seconds + " Sekunden gesetzt!");
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Bitte gib eine gültige Zahl an.");
                }
                break;

            case "whitelist":
                handleWhitelistCommand(sender, label, args);
                break;

            case "reload":
                reloadConfig();
                sender.sendMessage(ChatColor.GOLD + "[JoinCooldown] " + ChatColor.GREEN + "Konfiguration wurde neu geladen!");
                break;

            default:
                sender.sendMessage(ChatColor.RED + "Unbekannter Unterbefehl. Nutzung: /" + label + " <on|off|toggle|set|whitelist|reload>");
                break;
        }

        return true;
    }

    private void handleWhitelistCommand(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.GOLD + "[JoinCooldown] " + ChatColor.YELLOW + "Whitelist-Befehle:");
            sender.sendMessage(ChatColor.GRAY + "/" + label + " whitelist add <Spieler>");
            sender.sendMessage(ChatColor.GRAY + "/" + label + " whitelist remove <Spieler>");
            sender.sendMessage(ChatColor.GRAY + "/" + label + " whitelist list");
            return;
        }

        String action = args[1].toLowerCase();
        List<String> whitelist = getConfig().getStringList("Whitelist");

        if (action.equals("list")) {
            if (whitelist.isEmpty()) {
                sender.sendMessage(ChatColor.GOLD + "[JoinCooldown] " + ChatColor.YELLOW + "Die Whitelist ist zurzeit leer.");
            } else {
                sender.sendMessage(ChatColor.GOLD + "[JoinCooldown] " + ChatColor.YELLOW + "Gewhitelistete Spieler: " + ChatColor.GREEN + String.join(", ", whitelist));
            }
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Bitte gib einen Spielernamen an.");
            return;
        }

        String targetName = args[2];

        if (action.equals("add")) {
            if (whitelist.contains(targetName)) {
                sender.sendMessage(ChatColor.RED + targetName + " steht bereits auf der Whitelist.");
            } else {
                whitelist.add(targetName);
                getConfig().set("Whitelist", whitelist);
                saveConfig();
                sender.sendMessage(ChatColor.GOLD + "[JoinCooldown] " + ChatColor.GREEN + targetName + " wurde zur Whitelist hinzugefügt!");
            }
        } else if (action.equals("remove")) {
            if (whitelist.contains(targetName)) {
                whitelist.remove(targetName);
                getConfig().set("Whitelist", whitelist);
                saveConfig();
                sender.sendMessage(ChatColor.GOLD + "[JoinCooldown] " + ChatColor.RED + targetName + " wurde von der Whitelist entfernt!");
            } else {
                sender.sendMessage(ChatColor.RED + targetName + " ist nicht auf der Whitelist.");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Unbekannte Option. Nutze add, remove oder list.");
        }
    }

    // Auto-Tab-Completion für ingame Vorschläge
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("joincooldown.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return Arrays.asList("on", "off", "toggle", "set", "whitelist", "reload");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("whitelist")) {
            return Arrays.asList("add", "remove", "list");
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("whitelist") && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
            List<String> playerNames = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                playerNames.add(player.getName());
            }
            return playerNames;
        }

        return Collections.emptyList();
    }
}