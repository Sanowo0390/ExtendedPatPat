package jp.mintymc.patpat.velocity;

/** Select exactly one authority to prevent double-rendering cross-platform pats. */
final class PatModePolicy {
    enum Mode {
        NATIVE_CLIENT_MOD("native-client-mod"),
        OFFICIAL_BACKEND("official-backend"),
        DETECTING("detecting"),
        STANDALONE("standalone"),
        DISABLED("disabled");
        private final String status;
        Mode(String status){this.status=status;}
        String status(){return status;}
    }
    private PatModePolicy(){}
    static Mode resolve(boolean nativeMod,boolean officialEnabled,boolean helloReceived,
                        boolean allowFallback,long elapsedMs,long detectionMs) {
        if(nativeMod)return Mode.NATIVE_CLIENT_MOD;
        if(!officialEnabled)return Mode.STANDALONE;
        if(helloReceived)return Mode.OFFICIAL_BACKEND;
        if(elapsedMs<detectionMs)return Mode.DETECTING;
        return allowFallback?Mode.STANDALONE:Mode.DISABLED;
    }
}
