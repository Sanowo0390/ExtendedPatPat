package jp.mintymc.patpat.paper;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class PatPatVanillaPlugin extends JavaPlugin implements Listener, TabExecutor {
    private record Part(float x, float y, float w, float h) {}
    private static final Part[] HAND = {
        new Part(-0.15f,-0.16f,0.30f,0.29f), // palm
        new Part(-0.145f,0.105f,0.065f,0.215f),
        new Part(-0.068f,0.13f,0.068f,0.23f),
        new Part(0.012f,0.123f,0.067f,0.22f),
        new Part(0.091f,0.099f,0.062f,0.201f),
        new Part(0.129f,-0.15f,0.105f,0.165f) // thumb
    };
    private final Map<UUID,Long> lastPat = new HashMap<>();
    private final Set<UUID> disabled = new HashSet<>();
    private final Set<Animation> animations = new HashSet<>();

    @Override public void onEnable() {
        saveDefaultConfig();
        loadDisabled();
        Bukkit.getPluginManager().registerEvents(this, this);
        var command = getCommand("patpatvanilla");
        if (command != null) { command.setExecutor(this); command.setTabCompleter(this); }
    }
    @Override public void onDisable() {
        for (Animation animation : List.copyOf(animations)) animation.finish();
    }
    private void loadDisabled() {
        disabled.clear();
        for (String s : getConfig().getStringList("disabled-players")) {
            try { disabled.add(UUID.fromString(s)); }
            catch (IllegalArgumentException ex) { getLogger().warning("Invalid UUID in disabled-players: " + s); }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPat(PlayerInteractEntityEvent e) {
        if (!getConfig().getBoolean("enabled", true) || e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (!(e.getRightClicked() instanceof LivingEntity target) || target == p) return;
        if (!p.hasPermission("patpatvanilla.use") || disabled.contains(p.getUniqueId())) return;
        if (target instanceof Player other &&
                (!getConfig().getBoolean("interaction.allow-players", true) || disabled.contains(other.getUniqueId()))) return;
        if (getConfig().getBoolean("interaction.sneaking", true) && !p.isSneaking()) return;
        if (getConfig().getBoolean("interaction.empty-hand", true) &&
                !p.getInventory().getItemInMainHand().getType().isAir()) return;
        if (getConfig().getStringList("interaction.ignored-worlds").contains(p.getWorld().getName())) return;
        if (getConfig().getBoolean("interaction.cancel-interact", true)) e.setCancelled(true);

        long now = System.nanoTime();
        long cooldownNanos = Math.max(0L, getConfig().getLong("interaction.cooldown-ms", 250L)) * 1_000_000L;
        Long last = lastPat.get(p.getUniqueId());
        if (last != null && now - last < cooldownNanos) return;
        lastPat.put(p.getUniqueId(), now);
        play(p, target);
    }

    private void play(Player player, LivingEntity target) {
        if (getConfig().getBoolean("effects.swing-hand", true)) player.swingMainHand();
        if (getConfig().getBoolean("effects.hearts", false))
            target.getWorld().spawnParticle(Particle.HEART, target.getEyeLocation(), 1, 0.1,0.1,0.1,0.01);
        if (getConfig().getBoolean("sound.enabled", true)) {
            List<String> sounds = getConfig().getStringList("sound.ids");
            if (!sounds.isEmpty()) {
                String id = sounds.get(ThreadLocalRandom.current().nextInt(sounds.size()));
                float a = (float) getConfig().getDouble("sound.pitch-min", 0.9);
                float b = (float) getConfig().getDouble("sound.pitch-max", 1.1);
                float low = Math.min(a,b), high = Math.max(a,b);
                float pitch = low + ThreadLocalRandom.current().nextFloat() * (high-low);
                target.getWorld().playSound(target.getLocation(), id, SoundCategory.PLAYERS,
                    (float) getConfig().getDouble("sound.volume",0.65), pitch);
            }
        }
        if (getConfig().getBoolean("animation.enabled", true) &&
                animations.size() < Math.max(1, getConfig().getInt("animation.max-active",24)))
            new Animation(target).runTaskTimer(this,0L,1L);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage("§aPatPatVanilla §7v0.1.0 | active animations: " + animations.size());
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("patpatvanilla.admin")) { sender.sendMessage("§cNo permission."); return true; }
            reloadConfig(); loadDisabled(); sender.sendMessage("§aPatPatVanilla reloaded."); return true;
        }
        if (args[0].equalsIgnoreCase("toggle")) {
            if (!(sender instanceof Player player)) { sender.sendMessage("§cPlayers only."); return true; }
            boolean enabled = args.length > 1 ? args[1].equalsIgnoreCase("on") : disabled.contains(player.getUniqueId());
            if (enabled) disabled.remove(player.getUniqueId()); else disabled.add(player.getUniqueId());
            getConfig().set("disabled-players", disabled.stream().map(UUID::toString).sorted().toList());
            saveConfig();
            sender.sendMessage("§aPatPatVanilla: " + (enabled ? "§aON" : "§cOFF"));
            return true;
        }
        sender.sendMessage("§e/" + label + " <status|toggle [on|off]|reload>");
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("status","toggle","reload").stream()
                .filter(x -> x.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("toggle"))
            return List.of("on","off").stream().filter(x -> x.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }

    private final class Animation extends BukkitRunnable {
        private final LivingEntity target;
        private final List<BlockDisplay> blocks = new ArrayList<>();
        private final int ticks;
        private final double offsetY, depth;
        private int frame;
        private boolean done;

        private Animation(LivingEntity target) {
            this.target = target;
            this.ticks = Math.max(2,Math.min(40,getConfig().getInt("animation.ticks",6)));
            this.offsetY = getConfig().getDouble("animation.offset-y",0.16);
            this.depth = Math.max(0,getConfig().getDouble("animation.depth",0.425));
            float size = (float) Math.max(0.1,Math.min(4,getConfig().getDouble("animation.size",1.0)));
            float range = (float) Math.max(1,Math.min(64,getConfig().getDouble("animation.view-range",16)));
            Material material = Material.matchMaterial(getConfig().getString("animation.material","PINK_TERRACOTTA"));
            if (material == null || !material.isBlock() || material.isAir()) material = Material.PINK_TERRACOTTA;
            var blockData = material.createBlockData();
            Location origin = position(0);
            for (Part part : HAND) {
                BlockDisplay block = target.getWorld().spawn(origin,BlockDisplay.class,d -> {
                    d.setPersistent(false);
                    d.setGravity(false);
                    d.setInvulnerable(true);
                    d.setBillboard(Display.Billboard.CENTER);
                    d.setViewRange(range);
                    d.setShadowRadius(0);
                    d.setShadowStrength(0);
                    d.setBlock(blockData);
                    d.setTransformation(new Transformation(
                        new Vector3f(part.x()*size,part.y()*size,0),
                        new Quaternionf(),
                        new Vector3f(part.w()*size,part.h()*size,0.06f),
                        new Quaternionf()
                    ));
                });
                blocks.add(block);
            }
            animations.add(this);
        }

        private Location position(double down) {
            return target.getLocation().add(0,target.getHeight()+offsetY-down,0);
        }
        @Override public void run() {
            if (!target.isValid() || target.isDead() || frame > ticks) { finish(); return; }
            double t = (double) frame/ticks;
            double eased = 1.0-(1.0-t)*(1.0-t);
            Location p = position(depth*Math.sin(Math.PI*eased));
            for (BlockDisplay block : blocks) if (block.isValid()) block.teleport(p);
            frame++;
        }
        private void finish() {
            if (done) return;
            done = true;
            try { cancel(); } catch (IllegalStateException ignored) { }
            for (BlockDisplay block : blocks) if (block.isValid()) block.remove();
            blocks.clear(); animations.remove(this);
        }
    }
}
