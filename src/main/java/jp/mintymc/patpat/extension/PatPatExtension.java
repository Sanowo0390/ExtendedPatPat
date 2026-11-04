package jp.mintymc.patpat.extension;

import org.geysermc.event.subscribe.Subscribe;
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
    private final Map<GeyserSession,PatSession> sessions=new ConcurrentHashMap<>();
    private volatile boolean compatible;
    private volatile boolean cameraSupported=true;
    boolean cameraSupported() {return cameraSupported;}
    // Shared JDK-only bridge; no classloading dependency between Velocity and Geyser.
    private static final String PROXY_TO_BEDROCK="jp.mintymc.patpat.bridge.v1.java-to-bedrock";
    private static final String BEDROCK_TO_PROXY="jp.mintymc.patpat.bridge.v1.bedrock-to-java";
    private static final String BEDROCK_USERS="jp.mintymc.patpat.bridge.v1.bedrock-users";
    private static final String PROXY_PREFER_STANDALONE="jp.mintymc.patpat.bridge.v1.prefer-standalone";
    private final Set<UUID> bridgedBedrockUsers=ConcurrentHashMap.newKeySet();
    private final java.util.function.Consumer<Object[]> proxyBridge=this::receiveJavaPat;

    private ImageAnimation animation;
    private Path generatedPack;
    Settings settings() { return settings; }
    /** Use the Java proxy's standalone choice across both client editions.
     * The callback is a JDK interface, so separate plugin classloaders work.
     */
    boolean preferProxyStandalone() {
        Object pref=System.getProperties().get(PROXY_PREFER_STANDALONE);
        return pref instanceof java.util.function.BooleanSupplier supplier && supplier.getAsBoolean();
    }
    boolean patEnabled(GeyserSession session) {
        PatPreferences store=preferences;
        return store==null || store.enabled(session.getPlayerEntity().uuid());
    }
    boolean setPatEnabled(GeyserSession session,boolean enabled) throws IOException {
        PatPreferences store=preferences;
        if(store==null)throw new IOException("Pat preferences have not been initialized");
        return store.setEnabled(session.getPlayerEntity().uuid(),enabled);
    }
    ImageAnimation animation() { return animation; }
    @Subscribe public void pre(GeyserPreInitializeEvent event) {
        try {
            Files.createDirectories(dataFolder());
            preferences=new PatPreferences(dataFolder().resolve("disabled-players.txt"));
            copyDefault("config.properties");
            settings=Settings.load(dataFolder().resolve("config.properties"));
            copyDefaultImage();
            Path sounds=dataFolder().resolve("sounds");Files.createDirectories(sounds);
            for(String name:List.of("pat","pat1","pat2"))if(!Files.exists(sounds.resolve(name+".ogg")))try(InputStream in=getClass().getResourceAsStream("/"+name+".ogg")){if(in!=null)Files.copy(in,sounds.resolve(name+".ogg"));}
            Path folder=dataFolder().toAbsolutePath().normalize();
            Path source=folder.resolve(settings.imageFile()).normalize();
            if(!source.startsWith(folder))throw new IOException("image-file must be inside the extension folder");
            animation=ImageAnimation.load(source,settings.spriteColumns(),settings.spriteFrameMs());
            generatedPack=ImagePack.build(folder.resolve("generated"),animation,folder.resolve("sounds"),settings);
            logger().info("Image ready: "+settings.imageFile()+", "+animation.frames().size()+" frames, "+
                animation.duration()+" ms. Generated pack: "+generatedPack.getFileName());
        } catch(IOException e) { Properties disabled=new Properties();disabled.setProperty("enabled","false");settings=Settings.from(disabled);animation=null;generatedPack=null;logger().error("Unable to load configuration/image; bridge disabled",e); }
    }
    private void copyDefaultImage() throws IOException {
        Path path=dataFolder().resolve("images/hand.png");
        Files.createDirectories(path.getParent());
        if(!Files.exists(path))try(InputStream in=getClass().getResourceAsStream("/default-hand.png")) {
            if(in==null)throw new IOException("Missing bundled default-hand.png");
            Files.copy(in,path);
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
            if(generatedPack==null)return;
            ResourcePack pack=ResourcePack.create(PackCodec.path(generatedPack));
            if(event.resourcePacks().stream().noneMatch(p->p.uuid().equals(pack.uuid()))) event.register(pack);
        } catch(Exception e) {logger().error("Unable to register custom hand resource pack",e);}
    }
    @Subscribe public void permissions(GeyserRegisterPermissionsEvent event) {
        event.register("extendedpatpatgeyser.admin",TriState.NOT_SET);
    }
    @Subscribe public void post(GeyserPostInitializeEvent event) {
        // Geyser build hashes change every release. Probe runtime internals and
        // enable this extension if the methods actually used still exist.
        GeyserCompatibility.Report report=GeyserCompatibility.inspect();
        compatible=report.supported();
        cameraSupported=report.optionalMissing().isEmpty();
        String running=GeyserCompatibility.runtimeVersion();
        if(!compatible) {
            logger().error("PatPat disabled for Geyser "+running+
                ": unsupported internal API: "+String.join(", ",report.missing()));
            return;
        }
        if(!report.optionalMissing().isEmpty())
            logger().warning("Optional Geyser APIs missing on "+running+": "+
                String.join(", ",report.optionalMissing()));
        System.getProperties().put(PROXY_TO_BEDROCK,proxyBridge);
        System.getProperties().put(BEDROCK_USERS,bridgedBedrockUsers);
        logger().info("PatPat ready on Geyser "+running+
            " (runtime ABI check passed, no build or commit pin).");
        geyserApi().onlineConnections().forEach(c->{
            if(c instanceof GeyserSession session) safeAttach(session);
        });
    }
    private void safeAttach(GeyserSession session) {
        try {attach(session);}
        catch(LinkageError | RuntimeException incompatibleApi) {
            compatible=false;
            logger().error("Incompatible Geyser runtime during PatPat session attach; bridge disabled",incompatibleApi);
            if(System.getProperties().get(PROXY_TO_BEDROCK)==proxyBridge)
                System.getProperties().remove(PROXY_TO_BEDROCK);
            if(System.getProperties().get(BEDROCK_USERS)==bridgedBedrockUsers)
                System.getProperties().remove(BEDROCK_USERS);
            sessions.values().forEach(state->{
                try {state.close();}catch(LinkageError | RuntimeException ignored) {}
            });
            sessions.clear();
            bridgedBedrockUsers.clear();
        }
    }
    @Subscribe public void join(SessionJoinEvent event) { if(compatible && event.connection() instanceof GeyserSession s) safeAttach(s); }
    private void attach(GeyserSession session) {
        if(session.getDownstream()==null || sessions.containsKey(session)) return;
        PatSession state=new PatSession(this,session);
        if(sessions.putIfAbsent(session,state)!=null) return;
        if(session.getPlayerEntity()!=null && session.getPlayerEntity().uuid()!=null)
            bridgedBedrockUsers.add(session.getPlayerEntity().uuid());
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
                // The recipient's own player is not always present in Geyser's
                // ordinary entity UUID cache, so resolve it explicitly.
                boolean recipientIsTarget=other.getPlayerEntity()!=null
                        && targetUuid.equals(other.getPlayerEntity().uuid());
                Entity candidate=recipientIsTarget?other.getPlayerEntity()
                        :other.getEntityCache().getEntityByUuid(targetUuid);
                if(candidate==null || !candidate.isValid() || !targetUuid.equals(candidate.uuid())
                        || candidate.position()==null || candidate.position().distanceSquared(at)>16)return;
                viewer.showStandalone(candidate.javaId());
                if(recipientIsTarget)viewer.receivePatFeedback();
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
        if(event.connection() instanceof GeyserSession s) {
            if(s.getPlayerEntity()!=null) bridgedBedrockUsers.remove(s.getPlayerEntity().uuid());
            PatSession state=sessions.remove(s);if(state!=null)state.close();
        }
    }
    @Subscribe public void shutdown(GeyserShutdownEvent event) {
        if(System.getProperties().get(PROXY_TO_BEDROCK)==proxyBridge)
            System.getProperties().remove(PROXY_TO_BEDROCK);
        if(System.getProperties().get(BEDROCK_USERS)==bridgedBedrockUsers)
            System.getProperties().remove(BEDROCK_USERS);
        bridgedBedrockUsers.clear();
        sessions.values().forEach(PatSession::close);sessions.clear();
    }

    /** Bedrock-origin Pat events are forwarded only to Java viewers by the optional Velocity plugin. */
    void relayToJava(PatSession sender,Entity target) {
        if(!compatible || target==null || target.uuid()==null
                || sender.session().getPlayerEntity()==null)return;
        Object callback=System.getProperties().get(BEDROCK_TO_PROXY);
        if(callback instanceof java.util.function.Consumer<?> raw) {
            @SuppressWarnings("unchecked")
            java.util.function.Consumer<Object[]> bridge=(java.util.function.Consumer<Object[]>)raw;
            try {bridge.accept(new Object[]{sender.session().getPlayerEntity().uuid(),target.uuid()});}
            catch(RuntimeException exception) {logger().warning("Java bridge refused Bedrock Pat: "+exception.getMessage());}
        }
    }

    /** Java-origin Pat events: resolve UUID in each Bedrock viewer's own cache. */
    private void receiveJavaPat(Object[] message) {
        if(!compatible || !settings.enabled() || message==null || message.length!=2
                || !(message[0] instanceof UUID actor) || !(message[1] instanceof UUID target))return;
        sessions.values().forEach(viewer->{
            GeyserSession s=viewer.session();
            s.executeInEventLoop(()->{
                if(viewer.closed() || s.isClosed() || !patEnabled(s))return;
                Entity entity=s.getPlayerEntity()!=null && target.equals(s.getPlayerEntity().uuid())
                    ? s.getPlayerEntity():s.getEntityCache().getEntityByUuid(target);
                if(entity==null || !entity.isValid())return;
                viewer.showStandalone(entity.javaId());
                if(target.equals(s.getPlayerEntity().uuid()) && !actor.equals(target))
                    viewer.receivePatFeedback();
            });
        });
    }
    @Subscribe public void commands(GeyserDefineCommandsEvent event) {
        register(event,"status",false,(source,args)->{
            PatSession state=source.connection() instanceof GeyserSession s ? sessions.get(s) : null;
            if(state!=null) state.session().executeInEventLoop(()->source.sendMessage("ExtendedPatPat Geyser 0.13.0 / compatible="+compatible+" / backendHello="+state.ready()+" / standalone="+settings.standaloneMode()+" / personal="+patEnabled(state.session())+" / active="+state.active()+" / allowed="+settings.allows(source)+" / size="+settings.scale()+" / offsets="+settings.offsetSpace()));
            else source.sendMessage("ExtendedPatPat Geyser 0.13.0 / compatible="+compatible+" / sessions="+sessions.size());
        });
        register(event,"test",true,(source,args)->withSession(source,PatSession::test));
        register(event,"pat",true,(source,args)->withSession(source,PatSession::commandPat));
        // Always allow users to disable effects even without the optional use permission.
        register(event,"toggle",true,(source,args)->withSession(source,s->s.toggle(args),false));
        event.register(Command.builder(this).name("reload").source(CommandSource.class).description("Reload ExtendedPatPat Geyser config")
                .permission("extendedpatpatgeyser.admin",TriState.NOT_SET).executor((source,cmd,args)->{
                    try {
                        Settings loaded=Settings.load(dataFolder().resolve("config.properties"));
                        if(!loaded.imageFile().equals(settings.imageFile())
                                || loaded.handMotionEnabled()!=settings.handMotionEnabled()
                                || loaded.handMotionDepth()!=settings.handMotionDepth()
                                || loaded.handMotionDuration()!=settings.handMotionDuration()
                                || loaded.scale()!=settings.scale()
                                || loaded.spriteColumns()!=settings.spriteColumns()
                                || loaded.spriteFrameMs()!=settings.spriteFrameMs())
                            throw new IOException("画像/モーションの変更はリソースパック再生成のためProxy再起動が必要です。");
                        settings=loaded;sessions.values().forEach(s->s.session().executeInEventLoop(s::beginHandshake));
                        source.sendMessage("位置・表示時間・standalone-modeを再読み込みしました。画像、画像サイズ、手の上下動設定の変更にはProxy再起動が必要です。");
                    }
                    catch(IOException e){source.sendMessage("再読み込み失敗: "+e.getMessage());}
                }).build());
    }
    private void register(GeyserDefineCommandsEvent event,String name,boolean player,CommandAction action) {
        event.register(Command.builder(this).name(name).source(CommandSource.class).description("ExtendedPatPat Geyser "+name)
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
