package me.limbo99;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.Sound;
import org.bukkit.Particle;
import net.kyori.adventure.text.Component;

import java.util.*;

public class Limbo99 extends JavaPlugin implements Listener {

    private final Map<UUID, Long> punishedPlayers = new HashMap<>();
    private final Map<UUID, UUID> cameraView = new HashMap<>();
    private FileConfiguration cfg;
    private BossBar bossBar;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        cfg = getConfig();
        Bukkit.getPluginManager().registerEvents(this, this);

        bossBar = Bukkit.createBossBar("Limbo99", BarColor.PURPLE, BarStyle.SOLID);
        bossBar.setVisible(false);

        setupCommand();
        startCheckTask();

        getLogger().info("Limbo99 habilitado.");
    }

    @Override
    public void onDisable() {
        bossBar.removeAll();
    }

    private void setupCommand() {
        Objects.requireNonNull(getCommand("limbo")).setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Use in-game.");
                return true;
            }
            Player p = (Player) sender;
            if (args.length == 0) {
                p.sendMessage("§6Limbo99 §7- comandos: setloc | free <player> | reload | view <nick> | exitcam");
                return true;
            }

            String sub = args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "setloc":
                case "setdeathloc":
                    if (!p.hasPermission("limbo.admin")) {
                        p.sendMessage("Sem permissão.");
                        return true;
                    }
                    Location loc = p.getLocation();
                    cfg.set("death-world", loc.getWorld().getName());
                    cfg.set("death-x", loc.getX());
                    cfg.set("death-y", loc.getY());
                    cfg.set("death-z", loc.getZ());
                    cfg.set("death-yaw", loc.getYaw());
                    cfg.set("death-pitch", loc.getPitch());
                    saveConfig();
                    p.sendMessage("Local de morte definido para: " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
                    return true;
                case "free":
                case "freeplayer":
                    if (!p.hasPermission("limbo.admin")) {
                        p.sendMessage("Sem permissão.");
                        return true;
                    }
                    if (args.length < 2) {
                        p.sendMessage("Use: /limbo free <nick>");
                        return true;
                    }
                    Player target = Bukkit.getPlayer(args[1]);
                    if (target == null) {
                        p.sendMessage("Jogador não encontrado.");
                        return true;
                    }
                    if (punishedPlayers.containsKey(target.getUniqueId())) {
                        punishedPlayers.remove(target.getUniqueId());
                        freePlayer(target);
                        p.sendMessage("Jogador libertado.");
                    } else {
                        p.sendMessage("Jogador não está preso.");
                    }
                    return true;
                case "reload":
                    if (!p.hasPermission("limbo.admin")) {
                        p.sendMessage("Sem permissão.");
                        return true;
                    }
                    reloadConfig();
                    cfg = getConfig();
                    p.sendMessage("Config recarregada.");
                    return true;
                case "view":
                case "viewplayer":
                    if (!cfg.getBoolean("features.camera")) {
                        p.sendMessage("A câmera está desativada pelo servidor.");
                        return true;
                    }
                    if (!punishedPlayers.containsKey(p.getUniqueId())) {
                        p.sendMessage("Você não está preso.");
                        return true;
                    }
                    if (args.length != 2) {
                        p.sendMessage("Use: /limbo view <nick>");
                        return true;
                    }
                    Player tgt = Bukkit.getPlayer(args[1]);
                    if (tgt == null) {
                        p.sendMessage("Jogador offline.");
                        return true;
                    }
                    cameraView.put(p.getUniqueId(), tgt.getUniqueId());
                    p.setGameMode(GameMode.SPECTATOR);
                    p.setSpectatorTarget(tgt);
                    p.sendMessage("Agora você está vendo " + tgt.getName());
                    return true;
                case "exitcam":
                    if (cameraView.containsKey(p.getUniqueId())) {
                        cameraView.remove(p.getUniqueId());
                        p.setSpectatorTarget(null);
                        p.setGameMode(GameMode.ADVENTURE);
                        teleportToDeathLocation(p);
                        p.sendMessage("Você saiu da câmera.");
                    } else {
                        p.sendMessage("Você não está em câmera.");
                    }
                    return true;
                default:
                    p.sendMessage("Subcomando desconhecido.");
                    return true;
            }
        });
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();

        // Aplica punição ao respawnar
        Bukkit.getScheduler().runTaskLater(this, () -> punishPlayer(p), 5L);
    }


    private void punishPlayer(Player p) {
        punishedPlayers.put(p.getUniqueId(), System.currentTimeMillis());
        teleportToDeathLocation(p);

        if (cfg.getBoolean("features.effects")) {
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_VEX_AMBIENT, 1f, 0.5f);
            p.getWorld().spawnParticle(Particle.SOUL, p.getLocation().add(0,1,0), 40, 1, 1, 1, 0.05);
        }

        if (cfg.getBoolean("features.scoreboard")) {
            Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
            Team team;
            if (board.getTeam("Preso") == null) team = board.registerNewTeam("Preso");
            else team = board.getTeam("Preso");
            team.setPrefix("§c[PRESO] ");
            team.addEntry(p.getName());
            p.setScoreboard(board);
        }

        p.sendMessage("Você foi enviado ao Limbo por " + cfg.getInt("punish-time") + " horas.");
        bossBar.addPlayer(p);
        bossBar.setVisible(false);
    }

    private void teleportToDeathLocation(Player p) {
        World w = Bukkit.getWorld(cfg.getString("death-world", p.getWorld().getName()));
        if (w == null) w = p.getWorld();
        Location deathLoc = new Location(
                w,
                cfg.getDouble("death-x", w.getSpawnLocation().getX()),
                cfg.getDouble("death-y", w.getSpawnLocation().getY()),
                cfg.getDouble("death-z", w.getSpawnLocation().getZ()),
                (float) cfg.getDouble("death-yaw", 0f),
                (float) cfg.getDouble("death-pitch", 0f)
        );
        p.teleport(deathLoc);
    }

    private void freePlayer(Player p) {
        bossBar.removePlayer(p);
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        p.setScoreboard(main);
        Location spawn = p.getWorld().getSpawnLocation();
        p.teleport(spawn);
        p.sendMessage("Você foi libertado do Limbo.");
    }

    private void startCheckTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long punishTimeMillis = cfg.getInt("punish-time", 24) * 3600_000L;
                long now = System.currentTimeMillis();
                for (UUID uuid : new HashSet<>(punishedPlayers.keySet())) {
                    Long start = punishedPlayers.get(uuid);
                    Player p = Bukkit.getPlayer(uuid);
                    if (start == null) continue;
                    long elapsed = now - start;
                    long remaining = Math.max(0, punishTimeMillis - elapsed);

                    long seconds = remaining / 1000;
                    long hours = seconds / 3600;
                    long minutes = (seconds % 3600) / 60;
                    long sec = seconds % 60;

                    if (p != null) {
                        if (cfg.getBoolean("features.actionbar")) {
                            p.sendActionBar(
                                Component.text(
                                    String.format("Tempo restante: %02dh %02dm %02ds", hours, minutes, sec)
                                )
                            );
                        }

                        if (cfg.getBoolean("features.bossbar")) {
                            bossBar.setVisible(true);
                            double progress = Math.max(0.0, 1.0 - ((double) elapsed / (double) punishTimeMillis));
                            bossBar.setProgress(progress);
                            bossBar.setTitle(String.format("Limbo: %02dh %02dm %02ds", hours, minutes, sec));
                            if (!bossBar.getPlayers().contains(p)) bossBar.addPlayer(p);
                        } else {
                            bossBar.removePlayer(p);
                        }

                        if (cfg.getBoolean("features.effects") && p.getGameMode() != GameMode.SPECTATOR) {
                            p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, p.getLocation().add(0,1,0), 6, 0.4, 0.8, 0.4, 0);
                        }

                        if (cameraView.containsKey(p.getUniqueId())) {
                            UUID targetUuid = cameraView.get(p.getUniqueId());
                            Player target = Bukkit.getPlayer(targetUuid);
                            if (target == null) {
                                cameraView.remove(p.getUniqueId());
                                p.setSpectatorTarget(null);
                                p.setGameMode(GameMode.ADVENTURE);
                                teleportToDeathLocation(p);
                                p.sendMessage("A câmera foi encerrada (alvo desconectou).");
                            }
                        }
                    }

                    if (elapsed >= punishTimeMillis) {
                        punishedPlayers.remove(uuid);
                        if (p != null) freePlayer(p);
                    }
                }
            }
        }.runTaskTimer(Limbo99.this, 20L, 20L);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (punishedPlayers.containsKey(p.getUniqueId()) && p.getGameMode() != GameMode.SPECTATOR) {
            if (!e.getFrom().toVector().equals(e.getTo().toVector())) {
                e.setTo(e.getFrom());
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (punishedPlayers.containsKey(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (punishedPlayers.containsKey(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        if (punishedPlayers.containsKey(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }
}
