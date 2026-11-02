package jp.mintymc.patpat.extension;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

record Settings(boolean enabled, boolean autoPack, boolean sneak, boolean emptyHand, boolean players,
                double distance, long cooldown, long duration, long frameMs, double height, int maxActive,
                boolean localPreview, List<String> frames, String permission,
                String renderer, String imageFile, double scale, double offsetX, double offsetZ, boolean imageLoop, String offsetSpace, double sittingOffsetY, boolean soundEnabled, String soundEvent, double soundVolume, double soundPitch, boolean standaloneMode, boolean squishEnabled, boolean handMotionEnabled, double handMotionDepth, long handMotionDuration) {
    static Settings load(Path file) throws IOException {
        Properties p = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { p.load(reader); }
        return from(p);
    }
    static Settings from(Properties p) {
        List<String> frames = Arrays.stream(p.getProperty("frames", "\uE2A0,\uE2A1,\uE2A2,\uE2A3").split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).limit(32).toList();
        if (frames.isEmpty()) frames = List.of("\uE2A0", "\uE2A1", "\uE2A2", "\uE2A3");
        return new Settings(flag(p,"enabled",true), flag(p,"auto-pack",true), flag(p,"require-sneaking",true),
                flag(p,"require-empty-hand",true), flag(p,"allow-players",true),
                number(p,"max-distance",3,1,6), (long)number(p,"cooldown-ms",100,50,60000),
                (long)number(p,"duration-ms",0,0,60000), (long)number(p,"frame-ms",100,50,1000),
                number(p,"offset-y",number(p,"height-offset",0,-8,8),-8,8), (int)number(p,"max-active-per-viewer",32,1,128),
                flag(p,"local-preview",true), frames, p.getProperty("required-permission", "").trim(),
                p.getProperty("renderer","image").trim().toLowerCase(Locale.ROOT), p.getProperty("image-file","images/hand.gif").trim(),
                number(p,"image-scale",0.5,.05,8),number(p,"offset-x",0.5,-8,8),number(p,"offset-z",0,-8,8),flag(p,"image-loop",true),
                p.getProperty("offset-space","view").trim().toLowerCase(Locale.ROOT),number(p,"sitting-offset-y",0.2,-2,2),flag(p,"sound-enabled",true),p.getProperty("sound-event","patpatbedrock.pat"),number(p,"sound-volume",1,0,4),number(p,"sound-pitch",1,.1,4),flag(p,"standalone-mode",true),flag(p,"squish-enabled",true),
                flag(p,"hand-motion-enabled",true),number(p,"hand-motion-depth",0.425,0,2),
                (long)number(p,"hand-motion-duration-ms",240,50,5000));
    }
    boolean allows(org.geysermc.geyser.api.command.CommandSource source) {
        return permission.isBlank() || source.hasPermission(permission);
    }
    private static boolean flag(Properties p,String key,boolean fallback) {
        return Boolean.parseBoolean(p.getProperty(key,Boolean.toString(fallback)));
    }
    private static double number(Properties p,String key,double fallback,double min,double max) {
        try { double n=Double.parseDouble(p.getProperty(key,Double.toString(fallback)));
            return Double.isFinite(n) ? Math.clamp(n,min,max) : fallback;
        } catch (NumberFormatException e) { return fallback; }
    }
}
