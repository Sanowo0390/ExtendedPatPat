package jp.mintymc.patpat.extension;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Image-only settings: no legacy glyph renderer or old configuration aliases. */
record Settings(boolean enabled, boolean autoPack, boolean sneak, boolean emptyHand, boolean players,
                double distance, long cooldown, long duration, double height, int maxActive,
                boolean localPreview, String permission,
                String imageFile, int spriteColumns, int spriteFrameMs, double scale,
                double offsetX, double offsetZ, boolean imageLoop, String offsetSpace,
                double sittingOffsetY, boolean soundEnabled, String soundEvent,
                double soundVolume, double soundPitch, boolean standaloneMode,
                boolean squishEnabled, boolean handMotionEnabled, double handMotionDepth,
                long handMotionDuration, boolean swingHandEnabled,
                Set<String> ignoredEntityTypes, Set<UUID> ignoredPlayerUuids,
                boolean cameraShakeEnabled, double cameraShakeIntensity) {
    static Settings load(Path file) throws IOException {
        Properties props=new Properties();
        try(Reader reader=Files.newBufferedReader(file,StandardCharsets.UTF_8)){props.load(reader);}
        return from(props);
    }
    static Settings from(Properties p) {
        return new Settings(flag(p,"enabled",true),flag(p,"auto-pack",true),
            flag(p,"require-sneaking",true),flag(p,"require-empty-hand",true),
            flag(p,"allow-players",true),number(p,"max-distance",3,1,6),
            (long)number(p,"cooldown-ms",100,50,60000),
            (long)number(p,"duration-ms",0,0,60000),
            number(p,"offset-y",0,-8,8),
            (int)number(p,"max-active-per-viewer",32,1,128),
            flag(p,"local-preview",true),p.getProperty("required-permission","").trim(),
            p.getProperty("image-file","images/hand.png").trim(),
            (int)number(p,"image-sprite-columns",5,1,128),
            (int)number(p,"image-sprite-frame-ms",50,50,10000),
            number(p,"image-scale",0.5,.05,8),
            number(p,"offset-x",0.5,-8,8),
            number(p,"offset-z",0,-8,8),
            flag(p,"image-loop",true),
            p.getProperty("offset-space","view").trim().toLowerCase(Locale.ROOT),
            number(p,"sitting-offset-y",0.2,-2,2),
            flag(p,"sound-enabled",true),
            p.getProperty("sound-event","patpatbedrock.pat"),
            number(p,"sound-volume",1,0,4),
            number(p,"sound-pitch",1,.1,4),
            flag(p,"standalone-mode",true),flag(p,"squish-enabled",true),
            flag(p,"hand-motion-enabled",true),
            number(p,"hand-motion-depth",0.425,0,2),
            (long)number(p,"hand-motion-duration-ms",240,50,5000),
            flag(p,"swing-hand-enabled",true),entityTypes(p),playerUuids(p),
            flag(p,"camera-shake-enabled",false),
            number(p,"camera-shake-intensity",0.06,0,0.3));
    }
    private static Set<String> entityTypes(Properties p){
        Set<String> result=new HashSet<>();
        for(String raw:p.getProperty("ignored-entity-types","").split(",")){
            String type=raw.trim().toLowerCase(Locale.ROOT);
            if(type.matches("[a-z0-9_.-]+(:[a-z0-9_./-]+)?"))
                result.add(type.contains(":")?type:"minecraft:"+type);
        }
        return Set.copyOf(result);
    }
    private static Set<UUID> playerUuids(Properties p){
        Set<UUID> result=new HashSet<>();
        for(String raw:p.getProperty("ignored-player-uuids","").split(","))
            try {result.add(UUID.fromString(raw.trim()));}
            catch(IllegalArgumentException ignored){}
        return Set.copyOf(result);
    }
    boolean allows(org.geysermc.geyser.api.command.CommandSource source){
        return permission.isBlank() || source.hasPermission(permission);
    }
    private static boolean flag(Properties p,String key,boolean fallback){
        return Boolean.parseBoolean(p.getProperty(key,Boolean.toString(fallback)));
    }
    private static double number(Properties p,String key,double fallback,double min,double max){
        try{
            double n=Double.parseDouble(p.getProperty(key,Double.toString(fallback)));
            return Double.isFinite(n)?Math.clamp(n,min,max):fallback;
        }catch(NumberFormatException ex){return fallback;}
    }
}
