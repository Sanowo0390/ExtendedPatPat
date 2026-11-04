package jp.mintymc.patpat.paper;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HexFormat;
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
    private final Set<UUID> packReady=new HashSet<>();
    private DefaultPackHost defaultPack;
    private final SkinCatalog skinCatalog=SkinCatalog.load();
    private final Map<UUID,String> selectedSkins=new HashMap<>();
    private record ViewerSprite(Player viewer,ItemDisplay display,SkinCatalog.Skin skin) {}
    private SkinCatalog.Skin skin(Player viewer) {
        return skinCatalog.get(selectedSkins.getOrDefault(viewer.getUniqueId(),"default"));
    }
    private ItemStack handFrame(SkinCatalog.Skin skin,int index) {
        ItemStack item=new ItemStack(Material.PAPER);
        var meta=item.getItemMeta();
        meta.setItemModel(new NamespacedKey("patpat","hand_"+skin.id()+"_"+
            Math.min(skin.frames()-1,Math.max(0,index))));
        item.setItemMeta(meta);
        return item;
    }
    private boolean useResourcePack() {
        return getConfig().getString("animation.renderer","resourcepack").equalsIgnoreCase("resourcepack");
    }

    @Override public void onEnable() {
        saveDefaultConfig();
        loadDisabled();
        try {
            defaultPack=new DefaultPackHost(getDataFolder().toPath(),
                msg->getLogger().info(msg));
            if(getConfig().getBoolean("pack.http-enabled",false))
                defaultPack.start(getConfig().getString("pack.http-bind","127.0.0.1"),
                    getConfig().getInt("pack.http-port",8765));
            getLogger().info("Embedded default PatPat pack SHA1="+defaultPack.sha1());
        }catch(Exception error) {
            getLogger().severe("PatPat embedded pack unavailable: "+error);
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        var command = getCommand("patpatvanilla");
        if (command != null) { command.setExecutor(this); command.setTabCompleter(this); }
    }
    @Override public void onDisable() {
        for (Animation animation : List.copyOf(animations)) animation.finish();
        packReady.clear();
        if(defaultPack!=null)defaultPack.close();
    }
    /** ViaVersion may be installed on Paper or only at the proxy.
     * If it is only at the proxy, backend Paper cannot reliably read the original
     * client protocol; use Velocity mode for networks with mixed client versions.
     */
    private int protocol(Player player) {
        if(Bukkit.getPluginManager().getPlugin("ViaVersion")==null)return 777;
        try {
            Class<?> via=Class.forName("com.viaversion.viaversion.api.Via");
            Object api=via.getMethod("getAPI").invoke(null);
            Object result=api.getClass().getMethod("getPlayerVersion",UUID.class)
                .invoke(api,player.getUniqueId());
            if(result instanceof Number n && n.intValue()>0)return n.intValue();
        }catch(ReflectiveOperationException | LinkageError problem) {
            getLogger().fine("Cannot detect ViaVersion client protocol: "+problem.getMessage());
        }
        return 777;
    }
    private boolean spriteFor(Player viewer) {
        return useResourcePack() && protocol(viewer)>=769 &&
            (packReady.contains(viewer.getUniqueId()) ||
             getConfig().getBoolean("pack.assume-installed",false));
    }
    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Player player=event.getPlayer();
        packReady.remove(player.getUniqueId());
        Bukkit.getScheduler().runTaskLater(this,()->{
            if(player.isOnline())offerPack(player);
        },20L);
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        packReady.remove(event.getPlayer().getUniqueId());
    }
    @EventHandler public void onPackStatus(PlayerResourcePackStatusEvent event) {
        if(!DefaultPackId.ID.equals(event.getID()))return;
        if(event.getStatus()==PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED)
            packReady.add(event.getPlayer().getUniqueId());
        else if(event.getStatus()==PlayerResourcePackStatusEvent.Status.DECLINED ||
            event.getStatus()==PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD ||
            event.getStatus()==PlayerResourcePackStatusEvent.Status.FAILED_RELOAD ||
            event.getStatus()==PlayerResourcePackStatusEvent.Status.DISCARDED ||
            event.getStatus()==PlayerResourcePackStatusEvent.Status.INVALID_URL)
            packReady.remove(event.getPlayer().getUniqueId());
    }
    private void offerPack(Player player) {
        if(protocol(player)<769)return;
        String url=getConfig().getString("pack.url","");
        if(url==null || url.isBlank())return;
        if(!url.startsWith("https://") && !url.startsWith("http://")) {
            getLogger().warning("PatPat pack URL must be reachable via HTTP(S)");
            return;
        }
        try {
            byte[] hash=defaultPack==null?null:defaultPack.hash();
            String custom=getConfig().getString("pack.sha1","");
            if(custom!=null && !custom.isBlank()) {
                hash=HexFormat.of().parseHex(custom);
                if(hash.length!=20)throw new IllegalArgumentException("Pack SHA-1 must be 40 hex chars");
            }
            // addResourcePack, not setResourcePack: keep other plugins' packs intact.
            player.addResourcePack(DefaultPackId.ID,url,hash,
                "PatPat専用の手アニメーション（サーバーパックとは別）",false);
        }catch(Exception error){
            getLogger().warning("Could not offer PatPat resource pack: "+error);
        }
    }
    private static final class DefaultPackId {
        private static final UUID ID=
            UUID.fromString("cc05eb6d-9c58-5b4d-9d29-6bd46f1344cc");
    }
    private void loadDisabled() {
        disabled.clear();
        for (String s : getConfig().getStringList("disabled-players")) {
            try { disabled.add(UUID.fromString(s)); }
            catch (IllegalArgumentException ex) { getLogger().warning("Invalid UUID in disabled-players: " + s); }
        }
        selectedSkins.clear();
        ConfigurationSection preferences=getConfig().getConfigurationSection("selected-skins");
        if(preferences!=null)for(String uuid:preferences.getKeys(false)) {
            try {
                UUID id=UUID.fromString(uuid);
                String style=preferences.getString(uuid,"default");
                if(skinCatalog.contains(style))selectedSkins.put(id,style);
            }catch(IllegalArgumentException ignored) {}
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
            List<String> defaults=getConfig().getStringList("sound.ids");
            for(Player viewer:target.getWorld().getPlayers()) {
                if(viewer.getLocation().distanceSquared(target.getLocation())>1024 ||
                        disabled.contains(viewer.getUniqueId()))continue;
                SkinCatalog.Skin style=skin(viewer);
                String id=useResourcePack() && !style.sound().isBlank() ? style.sound()
                    :defaults.isEmpty()?null:defaults.get(ThreadLocalRandom.current().nextInt(defaults.size()));
                if(id==null)continue;
                float a=style.sound().isBlank()?(float)getConfig().getDouble("sound.pitch-min",0.9)
                    :style.pitchMin();
                float b=style.sound().isBlank()?(float)getConfig().getDouble("sound.pitch-max",1.1)
                    :style.pitchMax();
                float pitch=Math.min(a,b)+ThreadLocalRandom.current().nextFloat()*Math.abs(b-a);
                float volume=style.sound().isBlank()?(float)getConfig().getDouble("sound.volume",0.65)
                    :style.volume();
                viewer.playSound(target.getLocation(),id,SoundCategory.PLAYERS,volume,pitch);
            }
        }
        if (getConfig().getBoolean("animation.enabled", true) &&
                animations.size() < Math.max(1, getConfig().getInt("animation.max-active",24)))
            new Animation(target).runTaskTimer(this,0L,1L);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage("§aPatpatPaper §7v0.4.0 | active animations: " + animations.size()
                + (sender instanceof Player p?" | skin="+skin(p).id():""));
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("patpatvanilla.admin")) { sender.sendMessage("§cNo permission."); return true; }
            reloadConfig(); loadDisabled(); sender.sendMessage("§aPatPatVanilla reloaded."); return true;
        }
        if(args[0].equalsIgnoreCase("skin")) {
            if(!(sender instanceof Player player)) {
                sender.sendMessage("§cPlayers only.");return true;
            }
            if(args.length==1) {
                sender.sendMessage("§aPatPat skin: §e"+skin(player).id()+"§7 | /ppv skin list");return true;
            }
            if(args[1].equalsIgnoreCase("list")) {
                sender.sendMessage("§aAvailable skins: §e"+skinCatalog.description());return true;
            }
            String id=args[1].toLowerCase(Locale.ROOT);
            if(!skinCatalog.contains(id)) {
                sender.sendMessage("§cUnknown skin: "+id+" §7(/ppv skin list)");return true;
            }
            selectedSkins.put(player.getUniqueId(),id);
            getConfig().set("selected-skins."+player.getUniqueId(),id);
            saveConfig();
            sender.sendMessage("§aPatPat skin changed to §e"+id+" §7(visible only to you)");
            return true;
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
        sender.sendMessage("§e/" + label + " <status|skin [list|id]|toggle [on|off]|reload>");
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("status","skin","toggle","reload").stream()
                .filter(x -> x.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("toggle"))
            return List.of("on","off").stream().filter(x -> x.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("skin")) {
            List<String> values=new ArrayList<>();values.add("list");values.addAll(skinCatalog.ids());
            return values.stream().filter(x->x.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }

    private final class Animation extends BukkitRunnable {
        private final LivingEntity target;
        private final List<BlockDisplay> blocks = new ArrayList<>();
        private final List<Player> voxelViewers=new ArrayList<>();
        private final List<ViewerSprite> sprites=new ArrayList<>();
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
            for(Player viewer:target.getWorld().getPlayers()) {
                if(viewer.getLocation().distanceSquared(origin)>range*range ||
                        disabled.contains(viewer.getUniqueId()))continue;
                int client=protocol(viewer);
                if(client<762)continue; // Display entities require Java 1.19.4
                if(!spriteFor(viewer)) {
                    voxelViewers.add(viewer);
                    continue;
                }
                SkinCatalog.Skin style=skin(viewer);
                ItemDisplay sprite=target.getWorld().spawn(
                        origin.clone().add(style.offsetX(),style.offsetY(),style.offsetZ()),
                        ItemDisplay.class,d->{
                            d.setVisibleByDefault(false);
                            d.setPersistent(false);
                            d.setGravity(false);
                            d.setInvulnerable(true);
                            d.setBillboard(Display.Billboard.CENTER);
                            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                            d.setViewRange(range);
                            d.setShadowRadius(0);
                            d.setShadowStrength(0);
                            d.setItemStack(handFrame(style,0));
                            d.setTransformation(new Transformation(
                                new Vector3f(-size*style.scaleX()*0.5f,-size*style.scaleY()*0.5f,0),
                                new Quaternionf(),
                                new Vector3f(size*style.scaleX(),size*style.scaleY(),0.02f),
                                new Quaternionf()
                            ));
                        });
                viewer.showEntity(PatPatVanillaPlugin.this,sprite);
                sprites.add(new ViewerSprite(viewer,sprite,style));
            }
            if(!voxelViewers.isEmpty())for (Part part : HAND) {
                BlockDisplay block = target.getWorld().spawn(origin,BlockDisplay.class,d -> {
                    d.setVisibleByDefault(false);
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
                for(Player viewer:voxelViewers)viewer.showEntity(PatPatVanillaPlugin.this,block);
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
            for(ViewerSprite entry:sprites) {
                ItemDisplay display=entry.display();
                if(!display.isValid())continue;
                SkinCatalog.Skin style=entry.skin();
                display.teleport(p.clone().add(style.offsetX(),style.offsetY(),style.offsetZ()));
                display.setItemStack(handFrame(style,style.at(frame,ticks+1)));
            }
            frame++;
        }
        private void finish() {
            if (done) return;
            done = true;
            try { cancel(); } catch (IllegalStateException ignored) { }
            for (BlockDisplay block : blocks) if (block.isValid()) block.remove();
            for(ViewerSprite entry:sprites)if(entry.display().isValid())entry.display().remove();
            sprites.clear();
            blocks.clear(); animations.remove(this);
        }
    }
}
