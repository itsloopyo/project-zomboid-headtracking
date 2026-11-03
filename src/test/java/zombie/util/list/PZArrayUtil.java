package zombie.util.list;

import java.util.List;
import java.util.function.Predicate;

/**
 * The tests' stand-in for the game's class of the same name, whose contains a test's subject calls
 * as the game's AnimationPlayer does, counting the calls: a patch that answers without it is told
 * by the count, as the answer is the same. See zombie.core.properties.PropertyContainer.
 */
public final class PZArrayUtil {
    private PZArrayUtil() {}

    public static int calls;

    public static <E> boolean contains(List<E> list, Predicate<E> predicate) {
        calls++;
        return !list.isEmpty();
    }
}
