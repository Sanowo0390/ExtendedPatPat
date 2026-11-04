package jp.mintymc.patpat.velocity;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Experimental pack-free viewer-facing hand renderer.
 * RGBA PNGs are bundled in Velocity's JAR, never sent to the player.
 * Unicode full blocks approximate pixels, regular spaces preserve transparent cells.
 * Bold glyphs and underlines fill much of the vanilla font's built-in spacing.
 */
final class TextFrames {
    private static final Key FONT=Key.key("minecraft:uniform");
    private static final char FILLED='\u2588';
    // Java uniform font advances full blocks by ~10 pixels when bold;
    // vanilla ASCII spaces have a reliable invisible width of 4 pixels.
    private static final int BOLD_CELL_ADVANCE=10;
    private static final int SPACE_ADVANCE=4;
    private final int resolution;
    private final Map<String,Component[]> frames=new HashMap<>();

    TextFrames(SkinCatalog catalog,int resolution) {
        this.resolution=Math.clamp(resolution,8,32);
        for(String id:catalog.ids()){
            SkinCatalog.Skin skin=catalog.get(id);
            Component[] images=new Component[skin.frames()];
            for(int i=0;i<images.length;i++){
                String name="/text-art/"+id+"/"+i+".png";
                try(InputStream in=TextFrames.class.getResourceAsStream(name)){
                    if(in==null)throw new IllegalStateException("Missing bundled text art "+name);
                    BufferedImage png=ImageIO.read(in);
                    if(png==null)throw new IllegalStateException("Invalid bundled PNG "+name);
                    images[i]=render(png,this.resolution);
                }catch(Exception ex){
                    throw new IllegalStateException("Unable to load text art "+name,ex);
                }
            }
            frames.put(id,images);
        }
    }

    int resolution(){return resolution;}
    Component frame(SkinCatalog.Skin skin,int index){
        return frames.get(skin.id())[index];
    }

    static boolean useText(String setting,ClientVersion client){
        return "text_display".equalsIgnoreCase(setting.trim()) &&
            client!=null && !client.isOlderThan(ClientVersion.V_1_19_4);
    }

    static Component render(BufferedImage png,int size){
        int n=Math.clamp(size,8,32);
        BufferedImage small=new BufferedImage(n,n,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=small.createGraphics();
        try{
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(png,0,0,n,n,null);
        }finally{g.dispose();}
        TextComponent.Builder result=Component.text();
        for(int y=0;y<n;y++){
            if(y>0)result.append(Component.text("\n"));
            int x=0;
            while(x<n){
                int color=color(small.getRGB(x,y)),end=x+1;
                while(end<n && color(small.getRGB(end,y))==color)end++;
                if(color<0){
                    // U+2800 BRAILLE PATTERN BLANK was drawn as tiny dots / a
                    // missing glyph on real clients. Plain spaces render no ink.
                    result.append(Component.text(transparentRun(end-x)).font(FONT));
                }else{
                    // Bold extends every solid block toward its neighbour.
                    // Underline covers the built-in line gap beneath solid cells.
                    // Neither format is applied to transparent space segments.
                    Component glyph=Component.text(String.valueOf(FILLED).repeat(end-x))
                        .font(FONT).color(TextColor.color(color))
                        .bold(true).underlined(true);
                    result.append(glyph);
                }
                x=end;
            }
        }
        return result.build();
    }

    /** Best-effort invisible spacing: 2 or 3 ASCII spaces per transparent pixel. */
    static String transparentRun(int cells){
        if(cells<0 || cells>32)throw new IllegalArgumentException("Invalid transparent run");
        int spaces=(cells*BOLD_CELL_ADVANCE+SPACE_ADVANCE/2)/SPACE_ADVANCE;
        return " ".repeat(spaces);
    }

    private static int color(int argb){return (argb>>>24)<128?-1:argb&0xFFFFFF;}

    /** The vanilla text pivot is bottom centre; shift down to center on target. */
    static Vector3d position(Vector3d anchor,double imageSize,double down){
        return new Vector3d(anchor.getX(),anchor.getY()-imageSize/2+down,anchor.getZ());
    }
    static EntityData<Component> textMetadata(Component value){
        return new EntityData<>(23,EntityDataTypes.ADV_COMPONENT,value);
    }
    static List<EntityData<?>> spawnMetadata(Component value,double imageSize,int resolution){
        // Vanilla text: 1/40 block per glyph pixel; bold uniform cell ~10x10 px.
        float width=(float)(imageSize*40/(resolution*BOLD_CELL_ADVANCE));
        float height=(float)(imageSize*40/(resolution*10));
        return List.of(
            new EntityData<>(12,EntityDataTypes.VECTOR3F,new Vector3f(width,height,1f)),
            new EntityData<>(15,EntityDataTypes.BYTE,(byte)3), // BILLBOARD_CENTER
            new EntityData<>(17,EntityDataTypes.FLOAT,4f),
            textMetadata(value),
            new EntityData<>(24,EntityDataTypes.INT,1024),
            new EntityData<>(25,EntityDataTypes.INT,0), // transparent background
            new EntityData<>(26,EntityDataTypes.BYTE,(byte)-1),
            new EntityData<>(27,EntityDataTypes.BYTE,(byte)0) // no shadow
        );
    }
}
