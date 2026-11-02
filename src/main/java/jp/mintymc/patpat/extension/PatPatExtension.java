package jp.mintymc.patpat.extension;

import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.command.*;
import org.geysermc.geyser.api.event.bedrock.*;
import org.geysermc.geyser.api.event.lifecycle.*;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.api.pack.*;
import org.geysermc.geyser.api.util.TriState;
import org.geysermc.geyser.session.GeyserSession;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PatPatExtension implements Extension {
    private volatile Settings settings=Settings.from(new Properties());
    private final Map<GeyserSession,PatSession> sessions=new ConcurrentHashMap<>();
    private volatile boolean compatible;
    Settings settings() { return settings; }
    @Subscribe public void pre(GeyserPreInitializeEvent event) {
        try {
            Files.createDirectories(dataFolder());
            copyDefault("config.properties"); copyDefault("PatPatBedrock.mcpack");
            settings=Settings.load(dataFolder().resolve("config.properties"));
        } catch(IOException e) { settings=new Settings(false,false,true,true,true,3,600,800,100,.15,32,true,List.of("PAT"),""); logger().error("Unable to load configuration; bridge disabled",e); }
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
            ResourcePack pack=ResourcePack.create(PackCodec.path(dataFolder().resolve("PatPatBedrock.mcpack")));
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
        logger().info("PatPat extension ready for Geyser b1280. Paper addon is NOT needed. Backend PatPat Plugin 1.2.5+ is required.");
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
    @Subscribe public void quit(SessionDisconnectEvent event) {
        if(event.connection() instanceof GeyserSession s) { PatSession state=sessions.remove(s);if(state!=null)state.close(); }
    }
    @Subscribe public void shutdown(GeyserShutdownEvent event) { sessions.values().forEach(PatSession::close);sessions.clear(); }
    @Subscribe public void commands(GeyserDefineCommandsEvent event) {
        register(event,"status",false,(source,args)->{
            PatSession state=source.connection() instanceof GeyserSession s ? sessions.get(s) : null;
            if(state!=null) state.session().executeInEventLoop(()->source.sendMessage("PatPatExtension 0.2.1 / compatible="+compatible+" / backendHello="+state.ready()+" / active="+state.active()+" / allowed="+settings.allows(source)));
            else source.sendMessage("PatPatExtension 0.2.1 / compatible="+compatible+" / sessions="+sessions.size());
        });
        register(event,"test",true,(source,args)->withSession(source,s->s.test(args.length>0 && args[0].equalsIgnoreCase("text"))));
        register(event,"pat",true,(source,args)->withSession(source,PatSession::commandPat));
        event.register(Command.builder(this).name("reload").source(CommandSource.class).description("Reload PatPat extension config")
                .permission("patpatbedrock.admin",TriState.NOT_SET).executor((source,cmd,args)->{
                    try { settings=Settings.load(dataFolder().resolve("config.properties"));sessions.values().forEach(s->s.session().executeInEventLoop(s::beginHandshake));source.sendMessage("PatPat設定を再読み込みしました。パック配信設定はProxy再起動後に反映されます。"); }
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
        if(!settings.allows(source)){source.sendMessage("この操作にはProxy側の権限 "+settings.permission()+" が必要です。");return;}
        PatSession state=source.connection() instanceof GeyserSession s ? sessions.get(s) : null;
        if(state==null){source.sendMessage("このGeyserへ接続中の統合版プレイヤーから実行してください。対応ビルドも確認してください。");return;}
        state.session().executeInEventLoop(()->action.accept(state));
    }
    private interface CommandAction {void run(CommandSource source,String[] args);}
}
