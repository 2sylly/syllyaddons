package net.syllyaddons.domain;

public enum EvidenceKind {
    UNKNOWN(0),
    ESTIMATED(1),
    PUBLIC_DELAYED(2),
    PUBLIC_EXACT(3),
    DERIVED(3),
    LOCAL_EXACT(4);

    private final int reliability;

    EvidenceKind(int reliability) {
        this.reliability = reliability;
    }

    public int reliability() {
        return reliability;
    }
}
