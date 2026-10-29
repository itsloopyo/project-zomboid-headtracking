package com.cameraunlock.zomboid;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import zombie.scripting.ScriptManager;
import zombie.scripting.objects.Item;

/**
 * Work the game does over and over for an answer that never changes, done once. Each fix gives
 * the game's code the answer it would have worked out itself, so nothing the game does comes
 * out differently, in its own view or in first person, alone or in multiplayer.
 */
public final class GameFixes {
    private GameFixes() {}

    /**
     * The game keeps what a character is made of, its state machine among them, in a map by
     * kind, and the kind of a component is the class it descends from directly under the
     * game's root class of components. It finds that by climbing from the class asked for to
     * the root, on every asking, and a zombie's state machine alone is asked for dozens of
     * times a frame: with 300 zombies on the screen the climb and the map were a ninth of
     * everything the game's logic thread did. A class's ancestors do not change, so the game
     * makes the climb once a class: what its own code answered for a class is kept here, and
     * handed back the next time that class is asked about. Where it answered nothing, nothing
     * is kept and it works the answer out each time, as it did.
     */
    private static final ClassValue<Class<?>[]> KIND = new ClassValue<>() {
        @Override
        protected Class<?>[] computeValue(Class<?> asked) {
            return new Class<?>[1];
        }
    };

    /** What ECSComponent.getECSClass(Class) answered for this class before, or null where it has not yet. Called from the game's code. */
    public static Class<?> knownKind(Class<?> asked) {
        return asked == null ? null : KIND.get(asked)[0];
    }

    /** Keeps what ECSComponent.getECSClass(Class) is about to answer for a class. Called from the game's code. */
    public static void learnKind(Class<?> asked, Class<?> kind) {
        if (asked != null) {
            KIND.get(asked)[0] = kind;
        }
    }

    /**
     * Every candidate item of a loot roll is looked up by name four times, and a chunk seen for
     * the first time fills every container in it as it arrives, so a drive through a town the
     * save has not seen looks items up hundreds of thousands of times a second. Each lookup trims
     * the name, cuts a substring out of it, asks three or four maps for its module and the module
     * for the item, and for a name that finds nothing asks the same module again once for every
     * module the game has. What a name finds changes only when the game's scripts do: as they are
     * loaded or reloaded, as a module is read or reset, and as Lua makes an item or a copy of one,
     * each of which tells this class as it begins and as it ends (see Agent.itemLookupPatches).
     * That those are the only changes rests on reading the game's code, so ItemLookupGuard reads
     * each class as it is defined, the game's and any mod's, for code that could change what a
     * name finds without telling. So an item found is kept by its name, with the ScriptManager it
     * was found in and the count of changes begun and ended when it was found, and handed back to
     * a lookup in that ScriptManager while that count stands: the class's constructor is public,
     * so there can be more than one. Nothing is handed back or kept while a change is under way,
     * and a name that finds nothing is looked up each time. The lookup rolls no dice, so the loot
     * rolled with what is handed back is the loot the game would have rolled.
     */
    private record Found(ScriptManager scripts, Item item, long at) {}

    private static final ConcurrentHashMap<String, Found> FOUND = new ConcurrentHashMap<>();
    private static final AtomicInteger changing = new AtomicInteger();
    private static final AtomicLong changes = new AtomicLong();
    /**
     * 0 until the first lookup asks Agent.itemLookupsGap whether every change to the scripts tells
     * this class, then 1 if it does, -1 if not. -1 for good once stopKeepingItems has been called.
     */
    private static volatile int keeping;
    /** Why stopKeepingItems was called, or null while it has not been. */
    private static final AtomicReference<String> stoppedFor = new AtomicReference<>();

    /**
     * Dev only, and asked for with -Dcameraunlock.zomboid.checkItemLookups=true: each item handed
     * back is looked up the game's own way as well, a difference throws, and the lookups are
     * counted in the log every ten seconds.
     */
    private static final boolean CHECKED = DevBuild.ON && Boolean.getBoolean("cameraunlock.zomboid.checkItemLookups");
    private static final LongAdder handedBack = new LongAdder(), lookedUp = new LongAdder(), foundNothing = new LongAdder();
    private static volatile long saidAt = System.nanoTime();

    /** In place of the call ScriptManager.FindItem(String) makes to FindItem(String, boolean). Called from the game's code. */
    public static Item findItem(ScriptManager scripts, String name, boolean orBase) {
        long at = changes.get();
        if (name != null && changing.get() == 0 && keeping()) {
            Found found = FOUND.get(name);
            if (found != null && found.at() == at && found.scripts() == scripts) {
                if (CHECKED) {
                    Item own = scripts.FindItem(name, orBase);
                    if (own != found.item()) {
                        throw new IllegalStateException("ScriptManager.FindItem(\"" + name + "\") handed back " + found.item() + " where the game's own lookup finds " + own);
                    }
                    handedBack.increment();
                    say();
                }
                return found.item();
            }
            Item item = scripts.FindItem(name, orBase);
            if (item != null) {
                FOUND.put(name, new Found(scripts, item, at));
            }
            counted(item);
            return item;
        }
        Item item = scripts.FindItem(name, orBase);
        counted(item);
        return item;
    }

    /**
     * A change to what names find begins. Called from the game's code, first thing in each method
     * that makes one. A change made inside another clears nothing: whatever was kept before the
     * outer one began no longer has the count, and nothing is kept until it ends.
     */
    public static void scriptsChanging() {
        if (changing.incrementAndGet() == 1) {
            FOUND.clear();
        }
        changes.incrementAndGet();
    }

    /** The change ends, however its method leaves. Called from the game's code. */
    public static void scriptsChanged() {
        changes.incrementAndGet();
        if (changing.decrementAndGet() == 0) {
            FOUND.clear();
        }
    }

    private static boolean keeping() {
        int known = keeping;
        return known == 0 ? firstLookup() : known > 0;
    }

    private static synchronized boolean firstLookup() {
        if (keeping == 0) {
            keepItemsIf(Agent.itemLookupsGap());
            Log.info("by the first item lookup, " + Agent.itemLookupGuardCost());
        }
        return keeping > 0;
    }

    /**
     * Keeps what names find from now on if {@code gap}, why a change to the scripts could go by
     * untold, is null and stopKeepingItems has not been called. Otherwise nothing is kept and
     * every lookup is the game's own.
     */
    static synchronized void keepItemsIf(String gap) {
        FOUND.clear();
        keeping = gap == null ? 1 : -1;
        // Read after keeping is written, as stopKeepingItems writes keeping after this, so a stop
        // made at the same time cannot be undone by the line above.
        String stopped = stoppedFor.get();
        if (stopped != null) {
            keeping = -1;
        }
        String why = stopped != null ? stopped : gap;
        Log.info(why == null ? "ScriptManager.FindItem hands back what a name found before, until the game's scripts change"
                : "ScriptManager.FindItem looks every name up afresh, as the game does: " + why);
    }

    /**
     * Nothing is kept from now on, for good, and every lookup is the game's own: {@code why}
     * names code that could change what a name finds without telling this class. Called by
     * ItemLookupGuard as such a class is defined, before any of its code can run, and by the
     * patches of Agent.itemLookupPatches when one of them fails, before the class it was for is
     * given its new code, so it may be called while a class is being loaded and takes no lock
     * that a lookup holds. {@code cause} is what was thrown, where something was.
     */
    static void stopKeepingItems(String why, Throwable cause) {
        if (!stop(why)) {
            return;
        }
        String line = "ScriptManager.FindItem looks every name up afresh from now on, as the game does: " + why;
        if (cause == null) {
            Log.error(line);
        } else {
            Log.error(line, cause);
        }
    }

    /**
     * As stopKeepingItems, for a start after the game has loaded a class ItemLookupGuard could not
     * read as the game runs it: how the Workshop copy starts, so not a fault.
     */
    static void keepNoItemsAfterALateStart(String why) {
        if (stop(why)) {
            Log.info("ScriptManager.FindItem looks every name up afresh, as the game does, since " + why
                    + ". That is to be expected where the mod starts after the game has loaded its scripts, as the Workshop copy does");
        }
    }

    /** Whether this is the first stop. */
    private static boolean stop(String why) {
        if (!stoppedFor.compareAndSet(null, why)) {
            return false;
        }
        keeping = -1;
        FOUND.clear();
        return true;
    }

    private static void counted(Item item) {
        if (CHECKED) {
            lookedUp.increment();
            if (item == null) {
                foundNothing.increment();
            }
            say();
        }
    }

    private static void say() {
        long now = System.nanoTime();
        if (now - saidAt >= 10_000_000_000L) {
            saidAt = now;
            Log.info(String.format(Locale.ROOT, "item lookups checked so far: %d handed back from what was kept, each the item the game's own lookup finds,"
                    + " and %d looked up (%d of them found nothing), over %d changes to the scripts", handedBack.sum(), lookedUp.sum(), foundNothing.sum(), changes.get() / 2));
        }
    }
}
