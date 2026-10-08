package se.krka.kahlua.vm;

public interface JavaFunction {
    int call(LuaCallFrame callFrame, int nArguments);
}
