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
import org.bukkit.entity.ItemFrame;
import org.bukkit.map.MapView;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

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
    private final Map<UUID,Long> lastPat = new HashMap<>();
    private final Set<UUID> disabled = new HashSet<>();
    private final Set<Animation> animations = new HashSet<>();
    private PaperMapSkins mapSkins;
    private final Map<UUID,Set<Integer>> sentMaps=new HashMap<>();
    private final SkinCatalog skinCatalog=SkinCatalog.load();
    private final Map<UUID,String> selectedSkins=new HashMap<>();
    private SkinCatalog.Skin skin(Player viewer){
        return skinCatalog.get(selectedSkins.getOrDefault(viewer.getUniqueId(),"default"));
    }
    private static final class ViewerSprite {
        private final Player viewer;
        private final ItemFrame frame;
        private final SkinCatalog.Skin skin;
        private int lastFrame;
        ViewerSprite(Player viewer,ItemFrame frame,SkinCatalog.Skin skin){
            this.viewer=viewer;this.frame=frame;this.skin=skin;
        }
        Player viewer(){return viewer;}
        ItemFrame frame(){return frame;}
        SkinCatalog.Skin skin(){return skin;}
        int lastFrame(){return lastFrame;}
        void lastFrame(int n){lastFrame=n;}
    }

    @Override public void onEnable() {
        saveDefaultConfig();
        loadDisabled();
        try {
            mapSkins=new PaperMapSkins(this,skinCatalog,new MapFrames(skinCatalog));
            getLogger().info("URL-free PatPat map art ready ("+skinCatalog.ids().size()+" styles)");
        }catch(Exception ex){
            getLogger().severe("Could not initialize Java map art: "+ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        var command = getCommand("extendedpatpat");
        if (command != null) { command.setExecutor(this); command.setTabCompleter(this); }
    }
    @Override public void onDisable() {
        for (Animation animation : List.copyOf(animations)) animation.finish();
        sentMaps.clear();
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
    @EventHandler public void onQuit(PlayerQuitEvent event){
        sentMaps.remove(event.getPlayer().getUniqueId());
        lastPat.remove(event.getPlayer().getUniqueId());
    }
    private void ensureMaps(Player viewer,SkinCatalog.Skin skin){
        Set<Integer> sent=sentMaps.computeIfAbsent(viewer.getUniqueId(),key->new HashSet<>());
        for(MapView view:mapSkins.views(skin)){
            if(sent.add(view.getId()))viewer.sendMap(view);
        }
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
        if (!p.hasPermission("extendedpatpat.use") || disabled.contains(p.getUniqueId())) return;
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
                String id=defaults.isEmpty()?null:defaults.get(ThreadLocalRandom.current().nextInt(defaults.size()));
                if(id==null)continue;
                float a=(float)getConfig().getDouble("sound.pitch-min",0.9);
                float b=(float)getConfig().getDouble("sound.pitch-max",1.1);
                float pitch=Math.min(a,b)+ThreadLocalRandom.current().nextFloat()*Math.abs(b-a);
                float volume=(float)getConfig().getDouble("sound.volume",0.65);
                viewer.playSound(target.getLocation(),id,SoundCategory.PLAYERS,volume,pitch);
            }
        }
        if(getConfig().getBoolean("animation.enabled",true) &&
                animations.size()<Math.max(1,getConfig().getInt("animation.max-active",24))) {
            Animation animation=new Animation(target);
            if(animation.hasViewers())animation.runTaskTimer(this,0L,1L);
        }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage("§aExtendedPatPat Paper §7v0.5.0 | active animations: " + animations.size()
                + (sender instanceof Player p?" | skin="+skin(p).id():""));
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("extendedpatpat.admin")) { sender.sendMessage("§cNo permission."); return true; }
            reloadConfig(); loadDisabled(); sender.sendMessage("§aExtendedPatPat Paper reloaded."); return true;
        }
        if(args[0].equalsIgnoreCase("skin")) {
            if(!(sender instanceof Player player)) {
                sender.sendMessage("§cPlayers only.");return true;
            }
            if(args.length==1) {
                sender.sendMessage("§aPatPat skin: §e"+skin(player).id()+"§7 | /extendedpatpat skin list");return true;
            }
            if(args[1].equalsIgnoreCase("list")) {
                sender.sendMessage("§aAvailable skins: §e"+skinCatalog.description());return true;
            }
            String id=args[1].toLowerCase(Locale.ROOT);
            if(!skinCatalog.contains(id)) {
                sender.sendMessage("§cUnknown skin: "+id+" §7(/extendedpatpat skin list)");return true;
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
            sender.sendMessage("§aExtendedPatPat Paper: " + (enabled ? "§aON" : "§cOFF"));
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
        private final List<ViewerSprite> sprites=new ArrayList<>();
        private final int ticks;
        private final double offsetY,depth;
        private int tick;
        private boolean done;
        private Animation(LivingEntity target){
            this.target=target;
            this.ticks=Math.max(2,Math.min(40,getConfig().getInt("animation.ticks",6)));
            this.offsetY=getConfig().getDouble("animation.offset-y",0.16);
            this.depth=Math.max(0,getConfig().getDouble("animation.depth",0.425));
            float range=(float)Math.max(1,Math.min(64,getConfig().getDouble("animation.view-range",16)));
            Location origin=position(0);
            for(Player viewer:target.getWorld().getPlayers()){
                if(viewer.getLocation().distanceSquared(origin)>range*range ||
                    disabled.contains(viewer.getUniqueId()) || protocol(viewer)<393)continue;
                SkinCatalog.Skin style=skin(viewer);
                try{
                    ensureMaps(viewer,style);
                    MapView first=mapSkins.view(style,0);
                    ItemFrame item=target.getWorld().spawn(origin,ItemFrame.class,frame->{
                        frame.setVisibleByDefault(false);
                        frame.setPersistent(false);
                        frame.setVisible(false);
                        frame.setFixed(true); // No support block needed for short-lived art.
                        frame.setFacingDirection(facing(viewer,origin),true);
                        frame.setItem(mapSkins.item(style,0),false);
                        frame.setItemDropChance(0f);
                    });
                    viewer.showEntity(PatPatVanillaPlugin.this,item);
                    sprites.add(new ViewerSprite(viewer,item,style));
                }catch(Exception error){
                    getLogger().warning("Could not show map hand to "+viewer.getName()+": "+error.getMessage());
                }
            }
            if(hasViewers())animations.add(this);
        }
        private BlockFace facing(Player viewer,Location where){
            double dx=viewer.getLocation().getX()-where.getX();
            double dz=viewer.getLocation().getZ()-where.getZ();
            return Math.abs(dx)>Math.abs(dz)?
                (dx>=0?BlockFace.EAST:BlockFace.WEST):
                (dz>=0?BlockFace.SOUTH:BlockFace.NORTH);
        }
        boolean hasViewers(){return !sprites.isEmpty();}
        private Location position(double down){
            return target.getLocation().add(0,target.getHeight()+offsetY-down,0);
        }
        @Override public void run(){
            if(!target.isValid() || target.isDead() || tick>ticks){finish();return;}
            double t=(double)tick/ticks;
            double eased=1-(1-t)*(1-t);
            Location position=position(depth*Math.sin(Math.PI*eased));
            for(ViewerSprite view:sprites){
                ItemFrame frame=view.frame();
                if(!frame.isValid())continue;
                frame.teleport(position.clone().add(view.skin().offsetX(),view.skin().offsetY(),
                    view.skin().offsetZ()));
                int index=view.skin().at(tick,ticks+1);
                if(index!=view.lastFrame()){
                    frame.setItem(mapSkins.item(view.skin(),index),false);
                    view.lastFrame(index);
                }
            }
            tick++;
        }
        private void finish(){
            if(done)return;
            done=true;
            try{cancel();}catch(IllegalStateException ignored){}
            for(ViewerSprite view:sprites)if(view.frame().isValid())view.frame().remove();
            sprites.clear();
            animations.remove(this);
        }
    }
}
