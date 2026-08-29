package net.syllyaddons.compat.wynntils.v4_2_8;

import net.syllyaddons.domain.Evidence;
import net.syllyaddons.domain.EvidenceKind;

final class WynntilsEvidence {
    static final String VERSION = "4.2.8";

    private WynntilsEvidence() {}

    static Evidence local(long now, String note) {
        return new Evidence(EvidenceKind.LOCAL_EXACT, now, "wynntils-local-model", VERSION, note);
    }

    static Evidence publicModel(long now, String note) {
        return new Evidence(EvidenceKind.PUBLIC_EXACT, now, "wynntils-territory-model", VERSION, note);
    }
}
