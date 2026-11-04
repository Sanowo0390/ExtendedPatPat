package jp.mintymc.patpat.extension;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * One-time upgrade from the old ungrouped properties file.
 *
 * The new template owns comments/order, while users' raw property value lines
 * take precedence. A full verbatim backup is made before any rewrite.
 */
final class ConfigLayout {
    static final String MARKER = "ExtendedPatPat Geyser | Config Layout v2";
    private static final String LEGACY_MARKER = "PatPatGeyserExtension | Config Layout v2";
    private static final Pattern KEY=Pattern.compile("^\\s*([a-zA-Z][a-zA-Z0-9_.-]*)\\s*[=:].*$");
    private ConfigLayout() {}

    static boolean formatIfLegacy(Path file) throws IOException {
        String existing=Files.readString(file,StandardCharsets.UTF_8);
        if(existing.contains(MARKER) || existing.contains(LEGACY_MARKER))return false;

        String template;
        try(InputStream in=ConfigLayout.class.getResourceAsStream("/config.properties")) {
            if(in==null)throw new IOException("Bundled configuration template is missing");
            template=new String(in.readAllBytes(),StandardCharsets.UTF_8);
        }
        if(!template.contains(MARKER))throw new IOException("Unknown configuration template version");

        // Keep the *last* definition of a key, exactly as the user wrote it.
        // Raw lines preserve escaped Unicode, '=' in values, and Windows paths.
        Map<String,String> userValues=new LinkedHashMap<>();
        for(String line:existing.split("\\R",-1)) {
            Matcher m=KEY.matcher(line);
            if(m.matches())userValues.put(m.group(1),line);
        }
        StringBuilder formatted=new StringBuilder();
        Set<String> covered=new HashSet<>();
        for(String line:template.split("\\R",-1)) {
            Matcher m=KEY.matcher(line);
            if(m.matches()) {
                String key=m.group(1);
                covered.add(key);
                formatted.append(userValues.getOrDefault(key,line));
            } else formatted.append(line);
            formatted.append('\n');
        }
        boolean extra=false;
        for(var entry:userValues.entrySet())if(!covered.contains(entry.getKey())) {
            if(!extra) {
                formatted.append("\n# ----------------------------------------------------------------------\n");
                formatted.append("# 10. 旧設定/カスタム項目（元ファイルから保持）\n");
                formatted.append("# ----------------------------------------------------------------------\n");
                extra=true;
            }
            formatted.append(entry.getValue()).append('\n');
        }

        Path backup=file.resolveSibling(file.getFileName()+".backup-before-layout-v2");
        // Backup is never overwritten, even on a repeated formatting attempt.
        if(!Files.exists(backup))Files.copy(file,backup);
        Path temporary=Files.createTempFile(file.getParent(),"patpat-config-",".tmp");
        try {
            Files.writeString(temporary,formatted.toString(),StandardCharsets.UTF_8);
            try{Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException ignored){
                Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);
            }
        }finally{Files.deleteIfExists(temporary);}
        return true;
    }
}
