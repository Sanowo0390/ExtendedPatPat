package jp.mintymc.patpat.velocity;

/** Backend lease for queued cross-edition Pat relays. */
final class PatRelayPolicy {
    private PatRelayPolicy(){}
    static boolean sameOrigin(String acceptedBackend,String currentBackend,
            boolean sameView,boolean switching) {
        return sameView && !switching && acceptedBackend!=null && !acceptedBackend.isBlank()
            && acceptedBackend.equals(currentBackend);
    }
}
