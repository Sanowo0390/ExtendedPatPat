package jp.mintymc.patpat.extension;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Build-independent ABI preflight for the *internal* Geyser hooks used here.
 * The public extension API has its own version policy. Internal methods do not.
 * Report missing methods, fail closed when required hooks are incompatible.
 */
final class GeyserCompatibility {
    record Report(List<String> missing,List<String> optionalMissing) {
        boolean supported(){return missing.isEmpty();}
    }
    private GeyserCompatibility(){}

    static String runtimeVersion() {
        try {
            Class<?> impl=Class.forName("org.geysermc.geyser.GeyserImpl",
                false,GeyserCompatibility.class.getClassLoader());
            return impl.getField("VERSION").get(null)+
                " (build "+impl.getField("BUILD_NUMBER").get(null)+")";
        }catch(ReflectiveOperationException | LinkageError e) {
            return "unknown";
        }
    }

    static Report inspect() {
        ClassLoader loader=GeyserCompatibility.class.getClassLoader();
        List<String> missing=new ArrayList<>();
        List<String> optional=new ArrayList<>();
        probe(loader,missing,"org.geysermc.geyser.session.GeyserSession",
            "getDownstream/0","getEntityCache/0","getPlayerInventory/0",
            "getPlayerEntity/0","executeInEventLoop/1","scheduleInEventLoop/3",
            "sendUpstreamPacket/1","sendDownstreamGamePacket/1",
            "getWorldName/0","remoteServer/0","isClosed/0",
            "isSneaking/0","getGameMode/0","getMouseoverEntity/0");
        probe(loader,missing,"org.geysermc.geyser.session.DownstreamSession",
            "getSession/0","isState/1");
        probe(loader,missing,"org.geysermc.geyser.session.cache.EntityCache",
            "getEntityByJavaId/1","getEntityByUuid/1","nextEntityId/0",
            "getEntitiesUnsafe/0");
        probe(loader,missing,"org.geysermc.geyser.entity.type.Entity",
            "uuid/0","javaId/0","geyserId/0","position/0","isValid/0",
            "getFlag/1","getBoundingBoxWidth/0","getBoundingBoxHeight/0",
            "bedrockDefinition/0");
        probe(loader,missing,"org.geysermc.mcprotocollib.network.Session",
            "addListener/1","removeListener/1");
        for(String name:new String[]{
            "org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundInteractPacket",
            "org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket",
            "org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket",
            "org.cloudburstmc.protocol.bedrock.packet.MoveEntityAbsolutePacket",
            "org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket",
            "org.cloudburstmc.protocol.bedrock.packet.RemoveEntityPacket"}) {
            classPresent(loader,missing,name);
        }
        // Used only by optional camera shake. A missing camera API should not
        // prevent normal Pat animations.
        probe(loader,optional,"org.geysermc.geyser.session.GeyserSession",
            "camera/0","getSpectatedEntity/0");
        probe(loader,optional,"org.geysermc.geyser.impl.camera.CameraDefinitions",
            "getByFunctionality/2");
        return new Report(List.copyOf(missing),List.copyOf(optional));
    }

    private static void classPresent(ClassLoader loader,List<String> errors,String name) {
        try {Class.forName(name,false,loader);}
        catch(ClassNotFoundException | LinkageError e) {errors.add(name+" (missing class)");}
    }
    private static void probe(ClassLoader loader,List<String> errors,String path,String... methods) {
        Class<?> type;
        try {type=Class.forName(path,false,loader);}
        catch(ClassNotFoundException | LinkageError e) {
            errors.add(path+" (missing class)");return;
        }
        Method[] available;
        try {available=type.getMethods();}
        catch(LinkageError e) {errors.add(path+" (method linkage)");return;}
        for(String spec:methods) {
            int slash=spec.lastIndexOf('/');
            String method=spec.substring(0,slash);
            int arity=Integer.parseInt(spec.substring(slash+1));
            boolean found=false;
            for(Method m:available) {
                if(m.getName().equals(method) && m.getParameterCount()==arity) {
                    found=true;break;
                }
            }
            if(!found)errors.add(path+"#"+spec);
        }
    }
}
