package me.xenonspace;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class FloorPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private Material dangerousBlock = Material.AIR;
    private int changeInterval;
    private final List<Material> enabledBlocks = new ArrayList<>();
    private final List<Material> allPossibleBlocks = new ArrayList<>();
    private final Random random = new Random();
    private BukkitRunnable gameTask;
    private final String PREFIX = "§8[§6§lTHE FLOOR§8] §f";

    @Override
    public void onEnable() {
        // 1. Gestion de la configuration
        saveDefaultConfig();
        loadConfiguration();

        // 2. Scan de tous les blocs du jeu
        for (Material mat : Material.values()) {
            if (mat.isBlock() && mat.isSolid() && !mat.name().contains("LEGACY") && !mat.name().contains("AIR")) {
                allPossibleBlocks.add(mat);
            }
        }

        // 3. Enregistrement
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("floor") != null) {
            getCommand("floor").setExecutor(this);
            getCommand("floor").setTabCompleter(this);
        }

        startNewGameTask();
        getLogger().info("[TheFloorIsSomething] TheFloorIsSomething enabled!");
    }

    private void loadConfiguration() {
        changeInterval = getConfig().getInt("settings.timer", 30);
        enabledBlocks.clear();
        List<String> savedBlocks = getConfig().getStringList("enabled-blocks");
        for (String s : savedBlocks) {
            try {
                enabledBlocks.add(Material.valueOf(s));
            } catch (Exception ignored) {}
        }
    }

    private void saveToConfig() {
        getConfig().set("settings.timer", changeInterval);
        List<String> blockNames = enabledBlocks.stream().map(Enum::name).collect(Collectors.toList());
        getConfig().set("enabled-blocks", blockNames);
        saveConfig();
    }

    private void startNewGameTask() {
        if (gameTask != null) gameTask.cancel();
        gameTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (enabledBlocks.isEmpty()) return;
                dangerousBlock = enabledBlocks.get(random.nextInt(enabledBlocks.size()));
                String blockName = dangerousBlock.name().replace("_", " ");

                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendTitle("§c§lNEW DANGER!", "§fThe floor is now: §e" + blockName, 10, 70, 20);
                    p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                }
            }
        };
        gameTask.runTaskTimer(this, 0L, (long) changeInterval * 20L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        // Sécurité : Seuls les admins peuvent configurer
        if (!player.hasPermission("floor.admin")) {
            player.sendMessage(PREFIX + "§cYou don't have permission (floor.admin) to do that.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            player.sendMessage("§7§m-------§8[ §6§lTheFloorIsSomething Help §8]§7§m-------");
            player.sendMessage(" §6/floor help §7- §fShow this help menu");
            player.sendMessage(" §6/floor menu §7- §fSelect blocks to include");
            player.sendMessage(" §6/floor time <sec> §7- §fChange switch timer");
            player.sendMessage("§7§m---------------------------------------");
            return true;
        }

        if (args[0].equalsIgnoreCase("time") && args.length > 1) {
            try {
                changeInterval = Integer.parseInt(args[1]);
                startNewGameTask();
                saveToConfig(); // Sauvegarde le nouveau temps
                player.sendMessage(PREFIX + "Timer set to §e" + changeInterval + "s");
            } catch (NumberFormatException e) {
                player.sendMessage(PREFIX + "§cPlease enter a valid number.");
            }
        } else if (args[0].equalsIgnoreCase("menu")) {
            openSelectionMenu(player, 0);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1 && sender.hasPermission("floor.admin")) {
            completions.add("help");
            completions.add("menu");
            completions.add("time");
        }
        return completions;
    }

    public void openSelectionMenu(Player player, int page) {
        Inventory inv = Bukkit.createInventory(null, 54, "§0Select Blocks (Page " + (page + 1) + ")");

        int start = page * 45;
        for (int i = 0; i < 45 && (start + i) < allPossibleBlocks.size(); i++) {
            Material mat = allPossibleBlocks.get(start + i);
            addMenuItem(inv, i, mat);
        }

        if (page > 0) inv.setItem(45, createArrow("§6§l<- Previous Page", page - 1));
        inv.setItem(49, createArrow("§e§lCurrent Page: " + (page + 1), page));
        if ((page + 1) * 45 < allPossibleBlocks.size()) inv.setItem(53, createArrow("§6§lNext Page ->", page + 1));

        player.openInventory(inv);
    }

    private ItemStack createArrow(String name, int pageTarget) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            List<String> lore = new ArrayList<>();
            lore.add("§7Internal ID: " + pageTarget);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void addMenuItem(Inventory inv, int slot, Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            boolean isEnabled = enabledBlocks.contains(mat);
            meta.setDisplayName(isEnabled ? "§a" + mat.name() : "§c" + mat.name());
            List<String> lore = new ArrayList<>();
            lore.add(isEnabled ? "§7Status: §aActive" : "§7Status: §cInactive");
            lore.add("§eClick to toggle!");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        inv.setItem(slot, item);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().startsWith("§0Select Blocks")) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            Player player = (Player) event.getWhoClicked();

            if (clicked.getType() == Material.ARROW) {
                String loreLine = clicked.getItemMeta().getLore().get(0);
                int nextPage = Integer.parseInt(loreLine.split(": ")[1]);
                openSelectionMenu(player, nextPage);
                return;
            }

            Material mat = clicked.getType();
            if (enabledBlocks.contains(mat)) {
                enabledBlocks.remove(mat);
            } else {
                enabledBlocks.add(mat);
            }

            saveToConfig(); // Sauvegarde le changement de bloc immédiatement

            int currentPage = Integer.parseInt(event.getView().getTitle().replaceAll("[^0-9]", "")) - 1;
            openSelectionMenu(player, currentPage);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode().name().equals("CREATIVE") || player.getGameMode().name().equals("SPECTATOR")) return;

        Block block = player.getLocation().subtract(0, 0.1, 0).getBlock();
        if (block.getType() == dangerousBlock) {
            player.damage(2.0);
            player.setFireTicks(40);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
        }
    }
}