package se.krka.kahlua.vm;

public interface KahluaTable {
    KahluaTable getMetatable();
    Object rawget(Object key);
    Object rawget(int key);
    int len();
    void rawset(Object key, Object value);
    void rawset(int key, Object value);
}
