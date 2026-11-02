package jp.mintymc.patpat.extension;

import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.command.*;
import org.geysermc.geyser.api.event.bedrock.*;
import org.geysermc.geyser.api.event.lifecycle.*;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.api.entity.custom.CustomEntityDefinition;
import org.geysermc.geyser.api.pack.*;
import org.geysermc.geyser.api.util.TriState;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.entity.type.Entity;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PatPatExtension implements Extension {
    private volatile Settings settings=Settings.from(new Properties());
    private volatile PatPreferences preferences;
    private volatile PatStatistics statistics;
    private final Map<GeyserSession,PatSession> sessions=new ConcurrentHashMap<>();
    private volatile boolean compatible;
    private ImageAnimation animation;
    private Path generatedPack;
    Settings settings() { return settings; }
    boolean patEnabled(GeyserSession session) {
        PatPreferences store=preferences;
        return store==null || store.enabled(session.getPlayerEntity().uuid());
    }
    boolean setPatEnabled(GeyserSession session,boolean enabled) throws IOException {
        PatPreferences store=preferences;
        if(store==null)throw new IOException("Pat preferences have not been initialized");
        return store.setEnabled(session.getPlayerEntity().uuid(),enabled);
    }
    long patCount(GeyserSession session) {
        return statistics==null?0:statistics.total(session.getPlayerEntity().uuid());
    }
    void recordPat(GeyserSession session) {
        if(statistics==null)return;
        statistics.record(session.getPlayerEntity().uuid());
        if(statistics.shouldFlush())flushStatistics();
    }
    private void flushStatistics() {
        try {if(statistics!=null)statistics.flush();}
        catch(IOException e){logger().warning("Unable to save Pat statistics: "+e.getMessage());}
    }
    ImageAnimation animation() { return animation; }
    @Subscribe public void pre(GeyserPreInitializeEvent event) {
        try {
            Files.createDirectories(dataFolder());
            preferences=new PatPreferences(dataFolder().resolve("disabled-players.txt"));
            statistics=new PatStatistics(dataFolder().resolve("pat-stats.properties"));
            copyDefault("config.properties"); copyDefault("PatPatBedrock.mcpack");
            upgradeConfig();
            settings=Settings.load(dataFolder().resolve("config.properties"));
            if(!Set.of("image","glyph").contains(settings.renderer()))throw new IOException("renderer must be image or glyph");
            copyDefaultImage();
            Path sounds=dataFolder().resolve("sounds");Files.createDirectories(sounds);
            for(String name:List.of("pat","pat1","pat2"))if(!Files.exists(sounds.resolve(name+".ogg")))try(InputStream in=getClass().getResourceAsStream("/"+name+".ogg")){if(in!=null)Files.copy(in,sounds.resolve(name+".ogg"));}
            if(settings.renderer().equals("image")) {
                Path folder=dataFolder().toAbsolutePath().normalize();
                Path source=folder.resolve(settings.imageFile()).normalize();
                if(!source.startsWith(folder))throw new IOException("image-file must be inside the extension folder");
                animation=ImageAnimation.load(source);
                generatedPack=ImagePack.build(folder.resolve("generated"),animation,folder.resolve("sounds"),settings);
                logger().info("Image ready: "+settings.imageFile()+", "+animation.frames().size()+" frames, "+animation.duration()+" ms. Generated pack: "+generatedPack.getFileName());
            }
        } catch(IOException e) { Properties disabled=new Properties();disabled.setProperty("enabled","false");settings=Settings.from(disabled);animation=null;generatedPack=null;logger().error("Unable to load configuration/image; bridge disabled",e); }
    }
    private void upgradeConfig() throws IOException {
        Path file=dataFolder().resolve("config.properties");Properties old=new Properties();
        try(var reader=Files.newBufferedReader(file,java.nio.charset.StandardCharsets.UTF_8)){old.load(reader);}
        Map<String,String> added=new LinkedHashMap<>();
        added.put("renderer","image");added.put("image-file","images/hand.gif");added.put("image-scale","0.5");added.put("image-loop","true");added.put("offset-space","view");
        added.put("offset-x","0.5");added.put("offset-y",old.getProperty("height-offset","0.0"));added.put("offset-z","0.0");added.put("sitting-offset-y","0.2");added.put("standalone-mode","true");added.put("squish-enabled","true");added.put("hand-motion-enabled","true");added.put("hand-motion-depth","0.425");added.put("hand-motion-duration-ms","240");added.put("swing-hand-enabled","true");added.put("camera-shake-enabled","false");added.put("camera-shake-intensity","0.06");added.put("ignored-entity-types","");added.put("ignored-player-uuids","");
        StringBuilder append=new StringBuilder();
        added.forEach((key,value)->{if(!old.containsKey(key))append.append(key).append('=').append(value).append('\n');});
        if(!append.isEmpty())Files.writeString(file,"\n# Added image options: offset-space=view means X toward viewer, Y up, Z right.\n"+append,
                java.nio.charset.StandardCharsets.UTF_8,StandardOpenOption.APPEND);
    }
    private void copyDefaultImage() throws IOException {
        Path path=dataFolder().resolve("images/hand.gif");Files.createDirectories(path.getParent());
        if(!Files.exists(path))try(InputStream in=getClass().getResourceAsStream("/default-hand.gif")) {
            if(in==null)throw new IOException("Missing bundled default-hand.gif");Files.copy(in,path);
        }
    }
    @Subscribe public void entities(GeyserDefineEntitiesEvent event) {
        if(animation!=null)event.register(CustomEntityDefinition.of(ImagePack.ENTITY));
    }
    private void copyDefault(String name) throws IOException {
        Path destination=dataFolder().resolve(name);
        if(!Files.exists(destination)) try(InputStream in=getClass().getResourceAsStream('/'+name)) {
            if(in==null) throw new IOException("Missing bundled "+name);
            Files.copy(in,destination);
        }
    }
    @Subscribe public void packs(GeyserDefineResourcePacksEvent event) {
        if(!settings.autoPack()) return;
        try {
            if(settings.renderer().equals("image") && generatedPack==null)return;
            Path path=settings.renderer().equals("image")?generatedPack:dataFolder().resolve("PatPatBedrock.mcpack");
            ResourcePack pack=ResourcePack.create(PackCodec.path(path));
            if(event.resourcePacks().stream().noneMatch(p->p.uuid().equals(pack.uuid()))) event.register(pack);
        } catch(Exception e) {logger().error("Unable to register hand pack; text test remains available",e);}
    }
    @Subscribe public void permissions(GeyserRegisterPermissionsEvent event) {
        event.register("patpatbedrock.admin",TriState.NOT_SET);
    }
    @Subscribe public void post(GeyserPostInitializeEvent event) {
        // Internal packet/session APIs are pinned. Fail closed before installing any hooks.
        String running="unknown";
        try {
            // Reflection is intentional: javac would inline Geyser's static final build strings.
            String build=(String)GeyserImpl.class.getField("BUILD_NUMBER").get(null);
            String commit=(String)GeyserImpl.class.getField("COMMIT").get(null);
            running=(String)GeyserImpl.class.getField("VERSION").get(null);
            compatible=compatible(build,commit);
        } catch(ReflectiveOperationException | LinkageError e) {compatible=false;}
        if(!compatible) { logger().error("This build targets Geyser 2.12.1-b1280 (64bb399). Running "+running+". Bridge disabled to avoid incompatible packet hooks.");return; }
        logger().info("PatPat extension ready for Geyser b1280. Paper addon is NOT needed. Backend PatPat Plugin 1.2.5+ is optional; standalone Bedrock fallback is available.");
        geyserApi().onlineConnections().forEach(c->{ if(c instanceof GeyserSession s) attach(s); });
    }
    static boolean compatible(String build,String commit) {
        return "1280".equals(build) && "64bb3993b4beef6743af0ab76ee5dfc8e67a0d21".equals(commit);
    }
    @Subscribe public void join(SessionJoinEvent event) { if(compatible && event.connection() instanceof GeyserSession s) attach(s); }
    private void attach(GeyserSession session) {
        if(session.getDownstream()==null || sessions.containsKey(session)) return;
        PatSession state=new PatSession(this,session);
        if(sessions.putIfAbsent(session,state)!=null) return;
        session.getDownstream().getSession().addListener(state);
        session.executeInEventLoop(state::beginHandshake);
    }
    /**
     * Optional backend-free Pat. Each viewer owns a distinct Geyser entity cache,
     * so resolve the target by UUID on that viewer's event loop. Never broadcast by
     * Java entity ID: IDs may be reused on another Velocity backend.
     */
    void relayStandalone(PatSession sender,Entity target) {
        UUID targetUuid=target.uuid();
        if(targetUuid==null)return; // Actor has already received its own preview.
        GeyserSession actor=sender.session();
        var world=actor.getWorldName();
        var remote=actor.remoteServer();
        var at=target.position();
        if(world==null || remote==null || at==null)return;
        sessions.values().forEach(viewer->{
            if(viewer==sender)return;
            GeyserSession other=viewer.session();
            other.executeInEventLoop(()->{
                if(viewer.closed() || other.isClosed() || !settings.enabled()
                        || !Objects.equals(world,other.getWorldName())
                        || !Objects.equals(remote,other.remoteServer()))return;
                Entity candidate=other.getEntityCache().getEntityByUuid(targetUuid);
                if(candidate==null || !candidate.isValid() || !targetUuid.equals(candidate.uuid())
                        || candidate.position()==null || candidate.position().distanceSquared(at)>16)return;
                viewer.showStandalone(candidate.javaId());
            });
        });
    }
    /** Mirror the harmless hand swing to other Bedrock viewers in the same world. */
    void relaySwing(PatSession sender) {
        GeyserSession actor=sender.session();
        if(actor.getPlayerEntity()==null || actor.getPlayerEntity().uuid()==null)return;
        UUID actorUuid=actor.getPlayerEntity().uuid();
        var world=actor.getWorldName();
        var remote=actor.remoteServer();
        if(world==null || remote==null)return;
        sessions.values().forEach(viewer->{
            if(viewer==sender)return;
            GeyserSession other=viewer.session();
            other.executeInEventLoop(()->{
                if(viewer.closed() || other.isClosed() || !settings.enabled()
                        || !Objects.equals(world,other.getWorldName())
                        || !Objects.equals(remote,other.remoteServer()))return;
                Entity otherActor=other.getEntityCache().getEntityByUuid(actorUuid);
                if(otherActor!=null && otherActor.isValid())viewer.swingForViewer(otherActor.geyserId());
            });
        });
    }
    @Subscribe public void quit(SessionDisconnectEvent event) {
        if(event.connection() instanceof GeyserSession s) { PatSession state=sessions.remove(s);if(state!=null)state.close(); }
    }
    @Subscribe public void shutdown(GeyserShutdownEvent event) { flushStatistics();sessions.values().forEach(PatSession::close);sessions.clear(); }
    @Subscribe public void commands(GeyserDefineCommandsEvent event) {
        register(event,"status",false,(source,args)->{
            PatSession state=source.connection() instanceof GeyserSession s ? sessions.get(s) : null;
            if(state!=null) state.session().executeInEventLoop(()->source.sendMessage("PatPatExtension 0.9.0 / compatible="+compatible+" / backendHello="+state.ready()+" / standalone="+settings.standaloneMode()+" / personal="+patEnabled(state.session())+" / active="+state.active()+" / allowed="+settings.allows(source)+" / renderer="+settings.renderer()+" / size="+settings.scale()+" / offsets="+settings.offsetSpace()));
            else source.sendMessage("PatPatExtension 0.9.0 / compatible="+compatible+" / sessions="+sessions.size());
        });
        register(event,"test",true,(source,args)->withSession(source,s->s.test(args.length>0 && args[0].equalsIgnoreCase("text"))));
        register(event,"pat",true,(source,args)->withSession(source,PatSession::commandPat));
        // Always allow users to disable effects even without the optional use permission.
        register(event,"toggle",true,(source,args)->withSession(source,s->s.toggle(args),false));
        register(event,"stats",true,(source,args)->withSession(source,s->s.stats(),false));
        event.register(Command.builder(this).name("reload").source(CommandSource.class).description("Reload PatPat extension config")
                .permission("patpatbedrock.admin",TriState.NOT_SET).executor((source,cmd,args)->{
                    try {
                        Settings loaded=Settings.load(dataFolder().resolve("config.properties"));
                        if(!loaded.renderer().equals(settings.renderer()) || !loaded.imageFile().equals(settings.imageFile())
                                || (settings.renderer().equals("image") &&
                                    (loaded.handMotionEnabled()!=settings.handMotionEnabled()
                                    || loaded.handMotionDepth()!=settings.handMotionDepth()
                                    || loaded.handMotionDuration()!=settings.handMotionDuration()
                                    || loaded.scale()!=settings.scale())))
                            throw new IOException("画像/モーションの変更はリソースパック再生成のためProxy再起動が必要です。");
                        settings=loaded;sessions.values().forEach(s->s.session().executeInEventLoop(s::beginHandshake));
                        source.sendMessage("位置・表示時間・standalone-modeを再読み込みしました。画像、画像サイズ、手の上下動設定の変更にはProxy再起動が必要です。");
                    }
                    catch(IOException e){source.sendMessage("再読み込み失敗: "+e.getMessage());}
                }).build());
    }
    private void register(GeyserDefineCommandsEvent event,String name,boolean player,CommandAction action) {
        event.register(Command.builder(this).name(name).source(CommandSource.class).description("PatPat Bedrock "+name)
                // Velocity cannot apply Geyser's permission defaults. Keep public commands
                // visible; check an explicitly configured node at execution time instead.
                .permission("",TriState.TRUE).bedrockOnly(player).playerOnly(player)
                .executor((source,cmd,args)->action.run(source,args)).build());
    }
    private void withSession(CommandSource source,java.util.function.Consumer<PatSession> action) {
        withSession(source,action,true);
    }
    private void withSession(CommandSource source,java.util.function.Consumer<PatSession> action,boolean requireUsePermission) {
        if(requireUsePermission && !settings.allows(source)){source.sendMessage("この操作にはProxy側の権限 "+settings.permission()+" が必要です。");return;}
        PatSession state=source.connection() instanceof GeyserSession s ? sessions.get(s) : null;
        if(state==null){source.sendMessage("このGeyserへ接続中の統合版プレイヤーから実行してください。対応ビルドも確認してください。");return;}
        state.session().executeInEventLoop(()->action.accept(state));
    }
    private interface CommandAction {void run(CommandSource source,String[] args);}
}
