package jp.mintymc.patpat.paper;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Reuses a small fixed set of real Bukkit map IDs across plugin restarts.
 * Map pixel art is baked into the plugin and never downloads a resource pack.
 */
final class PaperMapSkins {
    private record Frames(MapView[] maps,ItemStack[] items) {}
    private final Map<String,Frames> assets=new HashMap<>();

    PaperMapSkins(JavaPlugin plugin,SkinCatalog catalog,MapFrames pixels) throws IOException {
        World world=Bukkit.getWorlds().stream().findFirst()
            .orElseThrow(()->new IOException("No loaded world for map allocation"));
        Path file=plugin.getDataFolder().toPath().resolve("map-ids.properties");
        Files.createDirectories(file.getParent());
        Properties saved=new Properties();
        if(Files.isRegularFile(file))try(InputStream input=Files.newInputStream(file)){
            saved.load(input);
        }
        for(String id:catalog.ids()){
            SkinCatalog.Skin skin=catalog.get(id);
            MapView[] views=new MapView[skin.frames()];
            ItemStack[] stacks=new ItemStack[skin.frames()];
            for(int frame=0;frame<views.length;frame++){
                String key=id+"."+frame;
                MapView map=null;
                String previous=saved.getProperty(key);
                if(previous!=null){
                    try{map=Bukkit.getMap(Integer.parseInt(previous));}
                    catch(IllegalArgumentException ignored){}
                }
                if(map==null)map=Bukkit.createMap(world);
                // Turn off ordinary cartography updates; these are static artwork.
                map.setTrackingPosition(false);
                map.setUnlimitedTracking(false);
                map.setLocked(true);
                for(MapRenderer renderer:List.copyOf(map.getRenderers()))map.removeRenderer(renderer);
                final byte[] values=pixels.frame(skin,frame);
                map.addRenderer(new MapRenderer(false){
                    private boolean rendered;
                    @SuppressWarnings("deprecation")
                    @Override public void render(MapView map,MapCanvas canvas,Player viewer){
                        if(rendered)return;
                        for(int y=0;y<128;y++)for(int x=0;x<128;x++)
                            canvas.setPixel(x,y,values[y*128+x]);
                        rendered=true;
                    }
                });
                ItemStack item=new ItemStack(Material.FILLED_MAP);
                MapMeta meta=(MapMeta)item.getItemMeta();
                meta.setMapView(map);
                item.setItemMeta(meta);
                views[frame]=map;stacks[frame]=item;
                saved.setProperty(key,Integer.toString(map.getId()));
            }
            assets.put(id,new Frames(views,stacks));
        }
        // Map IDs are stable on every subsequent restart, avoiding world map growth.
        Path tmp=Files.createTempFile(file.getParent(),"extendedpatpat-maps-",".tmp");
        try{
            try(OutputStream out=Files.newOutputStream(tmp)){
                saved.store(out,"ExtendedPatPat Paper static map IDs");
            }
            try{Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException ex){
                Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);
            }
        }finally{Files.deleteIfExists(tmp);}
    }
    MapView[] views(SkinCatalog.Skin style){return assets.get(style.id()).maps();}
    MapView view(SkinCatalog.Skin style,int frame){
        MapView[] views=views(style);
        return views[Math.max(0,Math.min(views.length-1,frame))];
    }
    ItemStack item(SkinCatalog.Skin style,int frame){
        ItemStack[] items=assets.get(style.id()).items();
        return items[Math.max(0,Math.min(items.length-1,frame))].clone();
    }
}
