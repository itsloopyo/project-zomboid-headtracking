package zombie.Lua;

public final class LuaManager {
    public static se.krka.kahlua.vm.KahluaTable env;
    public static se.krka.kahlua.integration.LuaCaller caller;
    public static se.krka.kahlua.j2se.J2SEPlatform platform;

    public static final class GlobalObject {
        public static void syncItemActivated(zombie.characters.IsoPlayer player, zombie.inventory.InventoryItem item) { throw new UnsupportedOperationException("reference stub"); }
    }
}
