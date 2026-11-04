package jp.mintymc.patpat.velocity;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.function.Consumer;

/** Bundled PatPat asset; independent of server.properties' normal pack stack. */
final class DefaultPackHost implements AutoCloseable {
    static final String FILE="PatPatJava-26.3.zip";
    static final String HTTP_PATH="/patpat/"+FILE;
    private final byte[] payload;
    private final byte[] hash;
    private final Consumer<String> log;
    private HttpServer http;
    DefaultPackHost(Path folder,Consumer<String> log) throws IOException {
        this.log=log;
        try(InputStream in=DefaultPackHost.class.getResourceAsStream("/"+FILE)) {
            if(in==null)throw new IOException("Embedded "+FILE+" not found; rebuild with build-all.yml");
            payload=in.readAllBytes();
        }
        try {hash=MessageDigest.getInstance("SHA-1").digest(payload);}
        catch(java.security.NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
        Files.createDirectories(folder);
        Path extracted=folder.resolve(FILE);
        if(!Files.exists(extracted) || !Arrays.equals(payload,Files.readAllBytes(extracted)))
            Files.write(extracted,payload);
    }
    byte[] hash(){return hash.clone();}
    String sha1(){return HexFormat.of().formatHex(hash);}
    void start(String bind,int port) throws IOException {
        if(port<1 || port>65535)throw new IOException("Invalid pack server port");
        http=HttpServer.create(new InetSocketAddress(bind,port),16);
        http.createContext(HTTP_PATH,this::serve);
        http.start();
        log.accept("PatPat pack endpoint listening on "+bind+":"+port+HTTP_PATH+
            " (configure externally reachable resource-pack-url for clients)");
    }
    private void serve(HttpExchange exchange) throws IOException {
        try {
            String method=exchange.getRequestMethod();
            if(!method.equals("GET") && !method.equals("HEAD")) {
                exchange.sendResponseHeaders(405,-1);return;
            }
            exchange.getResponseHeaders().set("Content-Type","application/zip");
            exchange.getResponseHeaders().set("Cache-Control","public, max-age=3600");
            exchange.getResponseHeaders().set("ETag",'"'+sha1()+'"');
            if(method.equals("HEAD")) {
                exchange.getResponseHeaders().set("Content-Length",Integer.toString(payload.length));
                exchange.sendResponseHeaders(200,-1);return;
            }
            exchange.sendResponseHeaders(200,payload.length);
            exchange.getResponseBody().write(payload);
        } finally {exchange.close();}
    }
    @Override public void close(){
        if(http!=null) {http.stop(0);http=null;}
    }
}
