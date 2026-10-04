package se.krka.kahlua.vm;

public interface KahluaTable {
    KahluaTable getMetatable();
    Object rawget(Object key);
}
