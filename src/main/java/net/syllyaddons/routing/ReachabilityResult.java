package net.syllyaddons.routing;

import java.util.Set;

public record ReachabilityResult(String ownerId, Set<String> reachable, Set<String> unreachable) {
    public ReachabilityResult {
        ownerId = ownerId == null ? "" : ownerId;
        reachable = Set.copyOf(reachable);
        unreachable = Set.copyOf(unreachable);
    }

    public boolean fullyConnected() {
        return !ownerId.isBlank() && unreachable.isEmpty();
    }
}
