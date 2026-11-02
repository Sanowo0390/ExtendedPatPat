package jp.mintymc.patpat.extension;

import com.google.gson.*;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Builds a dedicated custom-entity pack; it never replaces any font pages. */
final class ImagePack {
    static final String ENTITY="patpatbedrock:hand";
    static final UUID PACK_UUID=UUID.fromString("37261fb1-530a-4bd3-a9a8-c2e32bd88d88");
    private static final Gson JSON=new GsonBuilder().disableHtmlEscaping().create();
    private ImagePack(){}
    static Path build(Path directory,ImageAnimation animation) throws IOException {
        SortedMap<String,byte[]> entries=new TreeMap<>();
        Map<String,String> textures=new LinkedHashMap<>();List<String> array=new ArrayList<>();
        for(int i=0;i<animation.frames().size();i++) {
            String key="frame_"+i,file="textures/patpatbedrock/f"+i;
            ByteArrayOutputStream png=new ByteArrayOutputStream();ImageIO.write(animation.frames().get(i),"PNG",png);
            entries.put(file+".png",png.toByteArray());textures.put(key,file);array.add("Texture."+key);
        }
        put(entries,"entity/hand.entity.json",Map.of("format_version","1.10.0","minecraft:client_entity",Map.of("description",Map.of(
                "identifier",ENTITY,"materials",Map.of("default","entity_alphatest"),"textures",textures,
                "geometry",Map.of("default","geometry.patpatbedrock.hand"),
                "animations",Map.of("billboard","animation.patpatbedrock.billboard"),"scripts",Map.of("animate",List.of("billboard")),
                "render_controllers",List.of("controller.render.patpatbedrock.hand")))));
        put(entries,"render_controllers/hand.json",Map.of("format_version","1.8.0","render_controllers",Map.of("controller.render.patpatbedrock.hand",Map.of(
                "arrays",Map.of("textures",Map.of("Array.frames",array)),"geometry","Geometry.default",
                "materials",List.of(Map.of("*","Material.default")),"textures",List.of("Array.frames[query.variant]")))));
        put(entries,"models/entity/hand.geo.json",Map.of("format_version","1.12.0","minecraft:geometry",List.of(Map.of(
                "description",Map.of("identifier","geometry.patpatbedrock.hand","texture_width",128,"texture_height",128,
                        "visible_bounds_width",16,"visible_bounds_height",16,"visible_bounds_offset",List.of(0,0,0)),
                "bones",List.of(Map.of("name","billboard","pivot",List.of(0,0,0),"cubes",List.of(Map.of(
                        "origin",List.of(-8,-8,-.01),"size",List.of(16,16,.02),"uv",Map.of(
                        "north",Map.of("uv",List.of(0,0),"uv_size",List.of(128,128)),
                        "south",Map.of("uv",List.of(128,0),"uv_size",List.of(-128,128)))))))))));
        put(entries,"animations/hand.json",Map.of("format_version","1.8.0","animations",Map.of("animation.patpatbedrock.billboard",Map.of(
                "loop",true,"bones",Map.of("billboard",Map.of("rotation",List.of("-query.camera_rotation(0)","-query.camera_rotation(1)",0)))))));
        byte[] digest;
        try {
            MessageDigest hash=MessageDigest.getInstance("SHA-256");
            // Schema version and GIF timing are part of the identity, as well as texture bytes.
            hash.update(("image-pack-v1:"+animation.delays()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            entries.forEach((name,data)->{hash.update(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));hash.update(data);});digest=hash.digest();
        }catch(NoSuchAlgorithmException e){throw new IOException(e);}
        String token=HexFormat.of().formatHex(digest).substring(0,16);
        List<Integer> version=List.of(3,((digest[0]&255)<<8)|(digest[1]&255),((digest[2]&255)<<8)|(digest[3]&255));
        put(entries,"manifest.json",Map.of("format_version",2,"header",Map.of("name","PatPat Custom Image","description","Generated PNG/GIF hand display "+token,
                "uuid",PACK_UUID.toString(),"version",version,"min_engine_version",List.of(1,21,0)),
                "modules",List.of(Map.of("type","resources","uuid","68c0308c-d05b-4c21-b449-3f93d3aacdd6","version",version))));
        Files.createDirectories(directory);Path pack=directory.resolve("PatPatImages-"+token+".mcpack");
        if(Files.isRegularFile(pack))return pack;
        Path temp=Files.createTempFile(directory,"pack-",".tmp");
        try {
            try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(temp))) {
                for(var e:entries.entrySet()){ZipEntry entry=new ZipEntry(e.getKey());entry.setTime(0);zip.putNextEntry(entry);zip.write(e.getValue());zip.closeEntry();}
            }
            Files.move(temp,pack,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(temp);}
        return pack;
    }
    private static void put(Map<String,byte[]> entries,String name,Object value) {
        entries.put(name,JSON.toJson(canonical(JSON.toJsonTree(value))).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private static JsonElement canonical(JsonElement value) {
        if(value.isJsonObject()) {
            JsonObject result=new JsonObject();
            new TreeMap<>(value.getAsJsonObject().asMap()).forEach((key,item)->result.add(key,canonical(item)));return result;
        }
        if(value.isJsonArray()){JsonArray result=new JsonArray();value.getAsJsonArray().forEach(item->result.add(canonical(item)));return result;}
        return value;
    }
}
