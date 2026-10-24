package zombie.util.list;

import java.util.List;
import java.util.function.Predicate;

/** The tests' stand-in for the game's class of the same name, whose contains a test's subject calls as the game's AnimationPlayer does. See zombie.core.properties.PropertyContainer. */
public final class PZArrayUtil {
    private PZArrayUtil() {}

    public static <E> boolean contains(List<E> list, Predicate<E> predicate) {
        return !list.isEmpty();
    }
}
