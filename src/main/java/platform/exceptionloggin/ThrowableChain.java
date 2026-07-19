package platform.exceptionloggin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

final class ThrowableChain {
    private static final int MAX_CAUSE_DEPTH = 64;

    private ThrowableChain() {
    }

    static List<Throwable> from(Throwable error) {
        if (error == null) {
            return List.of();
        }
        List<Throwable> chain = new ArrayList<>();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = error;
        while (current != null && chain.size() < MAX_CAUSE_DEPTH && visited.add(current)) {
            chain.add(current);
            current = current.getCause();
        }
        return List.copyOf(chain);
    }

    static Throwable rootCause(Throwable error) {
        List<Throwable> chain = from(error);
        return chain.isEmpty() ? null : chain.get(chain.size() - 1);
    }
}
