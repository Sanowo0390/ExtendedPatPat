package jp.mintymc.patpat.velocity;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Properties;
import java.util.function.Consumer;
import javax.imageio.ImageIO;

/**
 * Generates player-head 8x8 sprite tiles using MineSkin's V2 queue endpoint.
 * Run only from an administrator command on Velocity's async scheduler.
 * Tokens are cached by content hash and never regenerated on normal Pat events.
 * The MineSkin API key stays on the server and is never transmitted to players.
 */
final class HeadTileGenerator {
    private static final URI QUEUE=URI.create("https://api.mineskin.org/v2/queue");
    private static final int TILE=8;
    private final HttpClient client=HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10)).build();

    record Token(String value,String signature) {
        Token {
            if(value==null || value.isBlank())throw new IllegalArgumentException("Empty skin token");
            signature=signature==null?"":signature;
        }
        String encoded(){return signature.isEmpty()?value:value+";"+signature;}
    }

    static BufferedImage prepareSkin(BufferedImage tile) {
        if(tile.getWidth()!=8 || tile.getHeight()!=8)
            throw new IllegalArgumentException("Expected 8x8 tile");
        BufferedImage skin=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics=skin.createGraphics();
        try{
            // Six first-layer head faces. No resource pack / extra skin layers.
            graphics.drawImage(tile,8,8,null);
            graphics.drawImage(tile,24,8,null);
            graphics.drawImage(tile,0,8,null);
            graphics.drawImage(tile,16,8,null);
            graphics.drawImage(tile,8,0,null);
            graphics.drawImage(tile,16,0,null);
        }finally{graphics.dispose();}
        return skin;
    }
    static boolean invisible(BufferedImage tile){
        for(int y=0;y<tile.getHeight();y++)
            for(int x=0;x<tile.getWidth();x++)
                if((tile.getRGB(x,y)>>>24)>=128)return false;
        return true;
    }

    static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        if(!ImageIO.write(image,"PNG",output))throw new IOException("PNG writer missing");
        return output.toByteArray();
    }

    static String sha256(byte[] data) {
        try{
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        }catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }

    void generate(Path cache,SkinCatalog.Skin skin,int resolution,String key,
                  Consumer<String> notice) throws IOException,InterruptedException {
        if(key==null || key.isBlank())throw new IOException("MineSkin API key is not configured");
        if(skin==null)throw new IOException("Unknown skin");
        resolution=HeadTileFrames.normalizeResolution(resolution);
        int side=resolution/TILE;
        Path images=cache.resolve(skin.id());
        Path tokens=cache.resolve("_tokens");
        Files.createDirectories(images);
        Files.createDirectories(tokens);
        for(int frame=0;frame<skin.frames();frame++){
            if(Thread.currentThread().isInterrupted())throw new InterruptedException();
            String resource="/text-art/"+skin.id()+"/"+frame+".png";
            BufferedImage original;
            try(InputStream in=HeadTileGenerator.class.getResourceAsStream(resource)){
                if(in==null)throw new IOException("Missing bundled frame: "+resource);
                original=ImageIO.read(in);
            }
            if(original==null)throw new IOException("Invalid bundled frame: "+resource);
            BufferedImage scaled=new BufferedImage(resolution,resolution,BufferedImage.TYPE_INT_ARGB);
            Graphics2D scaledG=scaled.createGraphics();
            try{
                scaledG.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                scaledG.drawImage(original,0,0,resolution,resolution,null);
            }finally{scaledG.dispose();}
            Properties frameProperties=new Properties();
            frameProperties.setProperty("resolution",Integer.toString(resolution));
            for(int y=0;y<side;y++)for(int x=0;x<side;x++){
                BufferedImage tile=scaled.getSubimage(x*TILE,y*TILE,TILE,TILE);
                if(invisible(tile))continue; // no texture generation for empty areas
                byte[] data=png(prepareSkin(tile));
                String digest=sha256(data);
                Path tokenPath=tokens.resolve(digest+".properties");
                Token token;
                if(Files.isRegularFile(tokenPath)){
                    Properties saved=read(tokenPath);
                    token=new Token(saved.getProperty("value",""),saved.getProperty("signature",""));
                }else{
                    notice.accept("MineSkin: "+skin.id()+" frame "+(frame+1)+"/"+skin.frames()
                        +" tile "+(y*side+x+1)+"/"+(side*side));
                    token=request(data,key,"ExtendedPatPat_"+skin.id()+"_"+frame+"_"+y+"_"+x);
                    // Save successful tiles individually to make interrupted jobs resumable.
                    Properties saved=new Properties();
                    saved.setProperty("value",token.value());
                    saved.setProperty("signature",token.signature());
                    write(tokenPath,saved);
                }
                frameProperties.setProperty("tile."+y+"."+x,token.encoded());
            }
            write(images.resolve(frame+".properties"),frameProperties);
            notice.accept("Cached "+skin.id()+" frame "+(frame+1)+"/"+skin.frames());
        }
    }

    private Token request(byte[] image,String key,String name) throws IOException,InterruptedException {
        String body="{\"name\":\""+name+"\",\"visibility\":\"unlisted\","
            +"\"variant\":\"classic\",\"url\":\"data:image/png;base64,"
            +Base64.getEncoder().encodeToString(image)+"\"}";
        HttpRequest.Builder builder=HttpRequest.newBuilder(QUEUE)
            .header("User-Agent","ExtendedPatPat-Velocity/0.7.0")
            .header("Accept","application/json")
            .header("Content-Type","application/json")
            .timeout(Duration.ofSeconds(25));
        String token=key.startsWith("Bearer ")?key:"Bearer "+key;
        HttpRequest req=builder.header("Authorization",token)
            .POST(HttpRequest.BodyPublishers.ofString(body,StandardCharsets.UTF_8)).build();
        for(int attempt=0;attempt<6;attempt++){
            HttpResponse<String> result=client.send(req,HttpResponse.BodyHandlers.ofString());
            if(result.statusCode()==200 || result.statusCode()==202){
                JsonObject obj=parse(result.body());
                Token success=skinFrom(obj);
                if(success!=null)return success;
                String id=field(obj,"job","id");
                if(id!=null && id.matches("[a-zA-Z0-9-]{1,72}"))return poll(id,key);
                throw new IOException("MineSkin returned neither signed skin nor queue job");
            }
            if(result.statusCode()==429 || result.statusCode()>=500){
                Thread.sleep(2500L*(attempt+1));continue;
            }
            throw new IOException("MineSkin returned HTTP "+result.statusCode()
                +" while submitting tile; check API key and quota");
        }
        throw new IOException("MineSkin queue retry limit exceeded");
    }

    private Token poll(String id,String key) throws IOException,InterruptedException {
        HttpRequest req=HttpRequest.newBuilder(
                URI.create("https://api.mineskin.org/v2/queue/"+id))
            .header("User-Agent","ExtendedPatPat-Velocity/0.7.0")
            .header("Accept","application/json")
            .header("Authorization",key.startsWith("Bearer ")?key:"Bearer "+key)
            .timeout(Duration.ofSeconds(18)).GET().build();
        for(int i=0;i<90;i++){
            Thread.sleep(1600);
            HttpResponse<String> response=client.send(req,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()==429){Thread.sleep(3500);continue;}
            if(response.statusCode()>=500)continue;
            if(response.statusCode()!=200)throw new IOException("MineSkin job HTTP "+response.statusCode());
            JsonObject obj=parse(response.body());
            Token success=skinFrom(obj);
            if(success!=null)return success;
            String state=field(obj,"job","status");
            if("failed".equalsIgnoreCase(state))
                throw new IOException("MineSkin tile generation failed");
        }
        throw new IOException("MineSkin job timed out");
    }

    static Token skinFrom(JsonObject root) {
        String value=field(root,"skin","texture","data","value");
        String signature=field(root,"skin","texture","data","signature");
        if(value==null)value=field(root,"data","texture","value");
        if(signature==null)signature=field(root,"data","texture","signature");
        return value==null?null:new Token(value,signature);
    }
    static JsonObject parse(String s) throws IOException {
        try{return JsonParser.parseString(s).getAsJsonObject();}
        catch(RuntimeException e){throw new IOException("Malformed MineSkin JSON response",e);}
    }
    static String field(JsonObject root,String... path){
        if(root==null)return null;
        JsonElement current=root;
        for(String name:path){
            if(current==null || !current.isJsonObject())return null;
            current=current.getAsJsonObject().get(name);
        }
        if(current==null || current.isJsonNull() || !current.isJsonPrimitive())return null;
        return current.getAsString();
    }
    private static Properties read(Path path) throws IOException {
        Properties p=new Properties();
        try(InputStream in=Files.newInputStream(path)){p.load(in);}
        return p;
    }
    private static void write(Path path,Properties p) throws IOException {
        Path tmp=Files.createTempFile(path.getParent(),".epp",".tmp");
        try{
            try(var out=Files.newOutputStream(tmp)){p.store(out,"ExtendedPatPat cached signed head tiles");}
            try{Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(java.nio.file.AtomicMoveNotSupportedException e){
                Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);
            }
        }finally{Files.deleteIfExists(tmp);}
    }
}
