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
    private static final String TOGGLE_READ="jp.mintymc.patpat.bridge.v1.toggle-read";
    private static final String TOGGLE_WRITE="jp.mintymc.patpat.bridge.v1.toggle-write";
    private static final String JAVA_TOGGLE_READ="jp.mintymc.patpat.bridge.v1.java-toggle-read";
    private final Set<UUID> bridgedBedrockUsers=ConcurrentHashMap.newKeySet();
    // Bedrock UUID may not exist at SessionJoin; stale quits must not
    // remove the UUID of a newer session for the same player.
    private final Map<GeyserSession,UUID> bedrockSessionIds=new ConcurrentHashMap<>();
    private final Map<UUID,GeyserSession> bedrockOwners=new ConcurrentHashMap<>();
    private final java.util.function.Consumer<Object[]> proxyBridge=this::receiveJavaPat;
    // Share one Bedrock preference store with /extendedpatpat on Velocity.
    private final java.util.function.Function<UUID,Boolean> toggleRead=this::patEnabled;
    private final java.util.function.Function<Object[],Boolean> toggleWrite=this::toggleFromProxy;

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
        if(session.getPlayerEntity()==null || session.getPlayerEntity().uuid()==null)return false;
        rememberBedrock(session);
        return patEnabled(session.getPlayerEntity().uuid());
    }
    void rememberBedrock(GeyserSession session) {
        if(session.getPlayerEntity()==null)return;
        UUID uuid=session.getPlayerEntity().uuid();
        if(uuid==null)return;
        UUID old=bedrockSessionIds.put(session,uuid);
        if(old!=null && !old.equals(uuid) && bedrockOwners.remove(old,session))
            bridgedBedrockUsers.remove(old);
        bedrockOwners.put(uuid,session);
        bridgedBedrockUsers.add(uuid);
    }
    private void forgetBedrock(GeyserSession session) {
        UUID uuid=bedrockSessionIds.remove(session);
        if(uuid!=null && bedrockOwners.remove(uuid,session))
            bridgedBedrockUsers.remove(uuid);
    }
    private boolean patEnabled(UUID uuid) {
        PatPreferences store=preferences;
        return store!=null && store.enabled(uuid);
    }
    /** Players who disabled Pat cannot be selected as targets, even if a
     * different viewer has their animations enabled.
     */
    boolean targetAllowsPat(Entity target) {
        if(!(target instanceof org.geysermc.geyser.entity.type.player.PlayerEntity) ||
                target.uuid()==null)return true;
        UUID uuid=target.uuid();
        if(bridgedBedrockUsers.contains(uuid))return patEnabled(uuid);
        Object callback=System.getProperties().get(JAVA_TOGGLE_READ);
        if(callback instanceof java.util.function.Function<?,?> raw){
            @SuppressWarnings("unchecked")
            var read=(java.util.function.Function<UUID,Boolean>)raw;
            try{return Boolean.TRUE.equals(read.apply(uuid));}
            catch(RuntimeException ex){
                logger().warning("Could not query Java target Pat preference: "+ex.getMessage());
            }
        }
        return true;
    }
    boolean setPatEnabled(GeyserSession session,boolean enabled) throws IOException {
        if(session.getPlayerEntity()==null)throw new IOException("Player is not ready");
        return setPatEnabled(session.getPlayerEntity().uuid(),enabled);
    }
    private boolean setPatEnabled(UUID uuid,boolean enabled) throws IOException {
        PatPreferences store=preferences;
        if(store==null)throw new IOException("Pat preferences have not been initialized");
        // PatPreferences returns the NEW enabled state (false for a valid OFF),
        // NOT an operation-success flag. IOException is the only write failure.
        store.setEnabled(uuid,enabled);
        if(!enabled) {
            sessions.values().forEach(state->{
                GeyserSession current=state.session();
                current.executeInEventLoop(()->{
                    if(current.getPlayerEntity()!=null && uuid.equals(current.getPlayerEntity().uuid()))
                        state.clear();
                    else state.hideTarget(uuid);
                });
            });
        }
        return true;
    }
    private Boolean toggleFromProxy(Object[] values) {
        if(values==null || values.length!=2 ||
                !(values[0] instanceof UUID uuid) ||
                !(values[1] instanceof Boolean enabled))return false;
        try {
            setPatEnabled(uuid,enabled);
            return true; // Explicit OFF is successful, even though its state is false.
        }catch(IOException ex) {
            logger().error("Unable to save Bedrock Pat toggle from Velocity",ex);
            return false;
        }
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
        System.getProperties().put(TOGGLE_READ,toggleRead);
        System.getProperties().put(TOGGLE_WRITE,toggleWrite);
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
            unregisterToggleBridge();
            sessions.values().forEach(state->{
                try {state.close();}catch(LinkageError | RuntimeException ignored) {}
            });
            sessions.clear();
            bedrockSessionIds.clear();
            bedrockOwners.clear();
            bridgedBedrockUsers.clear();
        }
    }
    @Subscribe public void join(SessionJoinEvent event) { if(compatible && event.connection() instanceof GeyserSession s) safeAttach(s); }
    private void attach(GeyserSession session) {
        if(session.getDownstream()==null || sessions.containsKey(session)) return;
        PatSession state=new PatSession(this,session);
        if(sessions.putIfAbsent(session,state)!=null) return;
        rememberBedrock(session);
        session.getDownstream().getSession().addListener(state);
        session.executeInEventLoop(state::beginHandshake);
    }
    /**
     * Optional backend-free Pat. Each viewer owns a distinct Geyser entity cache,
     * so resolve the target by UUID on that viewer's event loop. Never broadcast by
     * Java entity ID: IDs may be reused on another Velocity backend.
     */
    void relayStandalone(PatSession sender,Entity target) {
        if(!patEnabled(sender.session()) || !targetAllowsPat(target))return;
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
                        || !patEnabled(sender.session()) || !patEnabled(other)
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
        if(!patEnabled(sender.session()))return;
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
                        || !patEnabled(sender.session()) || !patEnabled(other)
                        || !Objects.equals(world,other.getWorldName())
                        || !Objects.equals(remote,other.remoteServer()))return;
                Entity otherActor=other.getEntityCache().getEntityByUuid(actorUuid);
                if(otherActor!=null && otherActor.isValid())viewer.swingForViewer(otherActor.geyserId());
            });
        });
    }
    @Subscribe public void quit(SessionDisconnectEvent event) {
        if(event.connection() instanceof GeyserSession s) {
            forgetBedrock(s);
            PatSession state=sessions.remove(s);if(state!=null)state.close();
        }
    }
    private void unregisterToggleBridge() {
        if(System.getProperties().get(TOGGLE_READ)==toggleRead)
            System.getProperties().remove(TOGGLE_READ);
        if(System.getProperties().get(TOGGLE_WRITE)==toggleWrite)
            System.getProperties().remove(TOGGLE_WRITE);
    }
    @Subscribe public void shutdown(GeyserShutdownEvent event) {
        unregisterToggleBridge();
        if(System.getProperties().get(PROXY_TO_BEDROCK)==proxyBridge)
            System.getProperties().remove(PROXY_TO_BEDROCK);
        if(System.getProperties().get(BEDROCK_USERS)==bridgedBedrockUsers)
            System.getProperties().remove(BEDROCK_USERS);
        bridgedBedrockUsers.clear();
        bedrockOwners.clear();
        bedrockSessionIds.clear();
        sessions.values().forEach(PatSession::close);sessions.clear();
    }

    /** Bedrock-origin Pat events are forwarded only to Java viewers by the optional Velocity plugin. */
    void relayToJava(PatSession sender,Entity target) {
        if(!compatible || !settings.enabled() || sender.closed() || sender.session().isClosed()
                || target==null || target.uuid()==null || !target.isValid()
                || sender.session().getPlayerEntity()==null
                || !patEnabled(sender.session()) || !targetAllowsPat(target))return;
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
                // A target UUID alone cannot prove the patter is in this
                // viewer's world. Require the Java actor and target in the SAME
                // recipient entity cache, near each other.
                Entity actorEntity=s.getEntityCache().getEntityByUuid(actor);
                if(!(actorEntity instanceof org.geysermc.geyser.entity.type.player.PlayerEntity)
                        || !actorEntity.isValid() || !actor.equals(actorEntity.uuid())
                        || !targetAllowsPat(actorEntity) || actorEntity.position()==null)return;
                boolean recipientIsTarget=s.getPlayerEntity()!=null
                    && target.equals(s.getPlayerEntity().uuid());
                Entity entity=recipientIsTarget?s.getPlayerEntity()
                    :s.getEntityCache().getEntityByUuid(target);
                if(entity==null || !entity.isValid() || !target.equals(entity.uuid())
                        || !targetAllowsPat(entity) || entity.position()==null)return;
                if(!PatBridgePolicy.sameWorldRange(actor,target,
                        actorEntity.position().distanceSquared(entity.position()),
                        settings.distance()))return;
                viewer.showStandalone(entity.javaId());
                if(recipientIsTarget)viewer.receivePatFeedback();
            });
        });
    }
    @Subscribe public void commands(GeyserDefineCommandsEvent event) {
        register(event,"status",false,(source,args)->{
            PatSession state=source.connection() instanceof GeyserSession s ? sessions.get(s) : null;
            if(state!=null) state.session().executeInEventLoop(()->source.sendMessage("ExtendedPatPat Geyser 0.13.2 / compatible="+compatible+" / backendHello="+state.ready()+" / standalone="+settings.standaloneMode()+" / personal="+patEnabled(state.session())+" / active="+state.active()+" / allowed="+settings.allows(source)+" / size="+settings.scale()+" / offsets="+settings.offsetSpace()));
            else source.sendMessage("ExtendedPatPat Geyser 0.13.2 / compatible="+compatible+" / sessions="+sessions.size());
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
