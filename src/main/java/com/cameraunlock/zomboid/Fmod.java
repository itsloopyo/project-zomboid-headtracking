package com.cameraunlock.zomboid;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;

/**
 * FMOD's own C interface, in the libraries the game has loaded, for what the game's Java
 * binding does not reach: the mix an event instance plays through, and effects put into it.
 *
 * The game's binding hands out FMOD's handles as numbers: an event instance and a bus are the
 * address FMOD gave for them, which is what is handed back here.
 */
@SuppressWarnings("restricted")
final class Fmod {
    /** FMOD_RESULT for a call that did what it was asked. */
    static final int OK = 0;
    /** FMOD_ERR_INVALID_HANDLE: the instance has been let go. */
    static final int INVALID_HANDLE = 30;
    /** FMOD_ERR_STUDIO_NOT_LOADED: an instance FMOD has not made its mix for yet, which it does at its next update. */
    static final int NOT_LOADED = 76;
    /** FMOD_ERR_ALREADY_LOCKED. */
    static final int ALREADY_LOCKED = 78;
    /** FMOD_CHANNELCONTROL_DSP_HEAD: the end of a mix's chain of effects nearest its output. */
    static final int HEAD = -1;
    /** FMOD_STUDIO_EVENT_CALLBACK_STARTING, _STARTED and _START_FAILED. */
    static final int STARTING = 0x4, STARTED = 0x8, START_FAILED = 0x40;
    /** FMOD_DSP_PARAMETER_TYPE_FLOAT, _INT and _DATA. */
    static final int FLOAT = 0, INT = 1, DATA = 3;

    private static final ValueLayout.OfInt INT_LAYOUT = ValueLayout.JAVA_INT;
    private static final ValueLayout.OfFloat FLOAT_LAYOUT = ValueLayout.JAVA_FLOAT;
    private static final AddressLayout PTR = ValueLayout.ADDRESS;
    private static final MethodHandle STUDIO_INSTANCE_VALID, STUDIO_INSTANCE_GROUP, STUDIO_INSTANCE_DESCRIPTION, STUDIO_DESCRIPTION_3D,
            STUDIO_DESCRIPTION_PATH, STUDIO_INSTANCE_PAUSE, STUDIO_INSTANCE_CALLBACK, STUDIO_BUS_VALID, STUDIO_BUS_LOCK, STUDIO_BUS_GROUP;
    private static final MethodHandle GROUP_SYSTEM, GROUP_ADD, GROUP_DSP, GROUP_DSPS, SYSTEM_CREATE_DSP, DSP_INFO,
            DSP_PARAMETERS, DSP_PARAMETER_INFO, DSP_SET_FLOAT, DSP_SET_INT, DSP_DATA, DSP_BYPASS, DSP_METERING,
            DSP_METERING_INFO, DSP_RELEASE, DSP_DISCONNECT;
    /** Out parameters and names, a block for each thread that calls in: the game's logic thread and, in a dev run, the probe's. */
    private static final ThreadLocal<MemorySegment> SCRATCH = ThreadLocal.withInitial(() -> Arena.global().allocate(1024, 16));

    static {
        Linker linker = Linker.nativeLinker();
        SymbolLookup studio = SymbolLookup.libraryLookup("fmodstudio.dll", Arena.global());
        SymbolLookup core = SymbolLookup.libraryLookup("fmod.dll", Arena.global());
        STUDIO_INSTANCE_VALID = handle(linker, studio, "FMOD_Studio_EventInstance_IsValid", PTR);
        STUDIO_INSTANCE_GROUP = handle(linker, studio, "FMOD_Studio_EventInstance_GetChannelGroup", PTR, PTR);
        STUDIO_INSTANCE_DESCRIPTION = handle(linker, studio, "FMOD_Studio_EventInstance_GetDescription", PTR, PTR);
        STUDIO_DESCRIPTION_3D = handle(linker, studio, "FMOD_Studio_EventDescription_Is3D", PTR, PTR);
        STUDIO_DESCRIPTION_PATH = handle(linker, studio, "FMOD_Studio_EventDescription_GetPath", PTR, PTR, INT_LAYOUT, PTR);
        STUDIO_INSTANCE_PAUSE = handle(linker, studio, "FMOD_Studio_EventInstance_SetPaused", PTR, INT_LAYOUT);
        STUDIO_INSTANCE_CALLBACK = handle(linker, studio, "FMOD_Studio_EventInstance_SetCallback", PTR, PTR, INT_LAYOUT);
        STUDIO_BUS_VALID = handle(linker, studio, "FMOD_Studio_Bus_IsValid", PTR);
        STUDIO_BUS_LOCK = handle(linker, studio, "FMOD_Studio_Bus_LockChannelGroup", PTR);
        STUDIO_BUS_GROUP = handle(linker, studio, "FMOD_Studio_Bus_GetChannelGroup", PTR, PTR);
        GROUP_SYSTEM = handle(linker, core, "FMOD_ChannelGroup_GetSystemObject", PTR, PTR);
        GROUP_ADD = handle(linker, core, "FMOD_ChannelGroup_AddDSP", PTR, INT_LAYOUT, PTR);
        GROUP_DSP = handle(linker, core, "FMOD_ChannelGroup_GetDSP", PTR, INT_LAYOUT, PTR);
        GROUP_DSPS = handle(linker, core, "FMOD_ChannelGroup_GetNumDSPs", PTR, PTR);
        SYSTEM_CREATE_DSP = handle(linker, core, "FMOD_System_CreateDSPByType", PTR, INT_LAYOUT, PTR);
        DSP_INFO = handle(linker, core, "FMOD_DSP_GetInfo", PTR, PTR, PTR, PTR, PTR, PTR);
        DSP_PARAMETERS = handle(linker, core, "FMOD_DSP_GetNumParameters", PTR, PTR);
        DSP_PARAMETER_INFO = handle(linker, core, "FMOD_DSP_GetParameterInfo", PTR, INT_LAYOUT, PTR);
        DSP_SET_FLOAT = handle(linker, core, "FMOD_DSP_SetParameterFloat", PTR, INT_LAYOUT, FLOAT_LAYOUT);
        DSP_SET_INT = handle(linker, core, "FMOD_DSP_SetParameterInt", PTR, INT_LAYOUT, INT_LAYOUT);
        DSP_DATA = handle(linker, core, "FMOD_DSP_GetParameterData", PTR, INT_LAYOUT, PTR, PTR, PTR, INT_LAYOUT);
        DSP_BYPASS = handle(linker, core, "FMOD_DSP_SetBypass", PTR, INT_LAYOUT);
        DSP_METERING = handle(linker, core, "FMOD_DSP_SetMeteringEnabled", PTR, INT_LAYOUT, INT_LAYOUT);
        DSP_METERING_INFO = handle(linker, core, "FMOD_DSP_GetMeteringInfo", PTR, PTR, PTR);
        DSP_RELEASE = handle(linker, core, "FMOD_DSP_Release", PTR);
        DSP_DISCONNECT = handle(linker, core, "FMOD_DSP_DisconnectAll", PTR, INT_LAYOUT, INT_LAYOUT);
    }

    private Fmod() {}

    private static MethodHandle handle(Linker linker, SymbolLookup library, String name, MemoryLayout... arguments) {
        return linker.downcallHandle(library.findOrThrow(name), FunctionDescriptor.of(INT_LAYOUT, arguments));
    }

    private static MemorySegment at(long address) {
        return MemorySegment.ofAddress(address);
    }

    // One for each shape of call, each exact, so that a call boxes nothing and leaves nothing for the collector.

    private static int call(MethodHandle handle, MemorySegment a) {
        try {
            return (int) handle.invokeExact(a);
        } catch (Throwable thrown) {
            throw failed(thrown);
        }
    }

    private static int call(MethodHandle handle, MemorySegment a, MemorySegment b) {
        try {
            return (int) handle.invokeExact(a, b);
        } catch (Throwable thrown) {
            throw failed(thrown);
        }
    }

    private static int call(MethodHandle handle, MemorySegment a, MemorySegment b, MemorySegment c) {
        try {
            return (int) handle.invokeExact(a, b, c);
        } catch (Throwable thrown) {
            throw failed(thrown);
        }
    }

    private static int call(MethodHandle handle, MemorySegment a, int b, MemorySegment c) {
        try {
            return (int) handle.invokeExact(a, b, c);
        } catch (Throwable thrown) {
            throw failed(thrown);
        }
    }

    private static int call(MethodHandle handle, MemorySegment a, MemorySegment b, int c) {
        try {
            return (int) handle.invokeExact(a, b, c);
        } catch (Throwable thrown) {
            throw failed(thrown);
        }
    }

    private static int call(MethodHandle handle, MemorySegment a, int b, int c) {
        try {
            return (int) handle.invokeExact(a, b, c);
        } catch (Throwable thrown) {
            throw failed(thrown);
        }
    }

    private static int call(MethodHandle handle, MemorySegment a, int b, float c) {
        try {
            return (int) handle.invokeExact(a, b, c);
        } catch (Throwable thrown) {
            throw failed(thrown);
        }
    }

    private static int call(MethodHandle handle, MemorySegment a, int b) {
        try {
            return (int) handle.invokeExact(a, b);
        } catch (Throwable thrown) {
            throw failed(thrown);
        }
    }

    private static int call(MethodHandle handle, MemorySegment a, MemorySegment b, int c, MemorySegment d) {
        try {
            return (int) handle.invokeExact(a, b, c, d);
        } catch (Throwable thrown) {
            throw failed(thrown);
        }
    }

    private static int call(MethodHandle handle, MemorySegment a, int b, MemorySegment c, MemorySegment d, MemorySegment e, int f) {
        try {
            return (int) handle.invokeExact(a, b, c, d, e, f);
        } catch (Throwable thrown) {
            throw failed(thrown);
        }
    }

    private static int call(MethodHandle handle, MemorySegment a, MemorySegment b, MemorySegment c, MemorySegment d, MemorySegment e, MemorySegment f) {
        try {
            return (int) handle.invokeExact(a, b, c, d, e, f);
        } catch (Throwable thrown) {
            throw failed(thrown);
        }
    }

    private static RuntimeException failed(Throwable thrown) {
        return thrown instanceof RuntimeException runtime ? runtime : new IllegalStateException(thrown);
    }

    /** Throws, naming what was asked, for any answer but OK. */
    static void check(int result, String what) {
        if (result != OK) {
            throw new IllegalStateException("FMOD " + what + " failed with FMOD_RESULT " + result);
        }
    }

    private static long pointerOut(MemorySegment scratch) {
        return scratch.get(PTR, 0).address();
    }

    static boolean instanceValid(long instance) {
        return call(STUDIO_INSTANCE_VALID, at(instance)) != 0;
    }

    /**
     * The mix an event instance plays through, or 0 with the FMOD_RESULT in {@code result[0]}
     * when FMOD has none for it: NOT_LOADED until its next update, INVALID_HANDLE once let go.
     */
    static long instanceGroup(long instance, int[] result) {
        MemorySegment scratch = SCRATCH.get();
        result[0] = call(STUDIO_INSTANCE_GROUP, at(instance), scratch);
        return result[0] == OK ? pointerOut(scratch) : 0L;
    }

    /**
     * Holds an instance where it is, or lets it go on, and says whether it is still there: one
     * the game has let go of answers INVALID_HANDLE. One started while held is held from its start.
     */
    static boolean pause(long instance, boolean paused) {
        int result = call(STUDIO_INSTANCE_PAUSE, at(instance), paused ? 1 : 0);
        if (result == INVALID_HANDLE) {
            return false;
        }
        check(result, "EventInstance_SetPaused");
        return true;
    }

    /**
     * A function of FMOD_STUDIO_EVENT_CALLBACK's shape that calls the static method named, found
     * through its class's own lookup, which takes (int type, MemorySegment instance,
     * MemorySegment parameters) and answers an FMOD_RESULT. FMOD calls it on its own Studio
     * thread: it must catch whatever it throws, as a throw out of it ends the game.
     */
    static MemorySegment eventCallback(MethodHandles.Lookup owner, String method) {
        try {
            MethodHandle target = owner.findStatic(owner.lookupClass(), method, MethodType.methodType(int.class, int.class, MemorySegment.class, MemorySegment.class));
            return Linker.nativeLinker().upcallStub(target, FunctionDescriptor.of(INT_LAYOUT, INT_LAYOUT, PTR, PTR), Arena.global());
        } catch (ReflectiveOperationException missing) {
            throw new IllegalStateException(missing);
        }
    }

    /** Has FMOD call back on the instance's events of the mask's types, and says whether the instance is still there. */
    static boolean setCallback(long instance, MemorySegment callback, int mask) {
        int result = call(STUDIO_INSTANCE_CALLBACK, at(instance), callback, mask);
        if (result == INVALID_HANDLE) {
            return false;
        }
        check(result, "EventInstance_SetCallback");
        return true;
    }

    /** The event an instance plays, or 0 once the instance is let go. */
    static long instanceDescription(long instance) {
        MemorySegment scratch = SCRATCH.get();
        int result = call(STUDIO_INSTANCE_DESCRIPTION, at(instance), scratch);
        if (result == INVALID_HANDLE) {
            return 0L;
        }
        check(result, "EventInstance_GetDescription");
        return pointerOut(scratch);
    }

    static boolean description3D(long description) {
        MemorySegment scratch = SCRATCH.get();
        check(call(STUDIO_DESCRIPTION_3D, at(description), scratch), "EventDescription_Is3D");
        return scratch.get(INT_LAYOUT, 0) != 0;
    }

    /** An event's path, as event:/..., which needs the strings bank the game loads. */
    static String descriptionPath(long description) {
        MemorySegment scratch = SCRATCH.get();
        check(call(STUDIO_DESCRIPTION_PATH, at(description), scratch.asSlice(16), 512, scratch), "EventDescription_GetPath");
        return scratch.asSlice(16).getString(0, StandardCharsets.UTF_8);
    }

    static boolean busValid(long bus) {
        return call(STUDIO_BUS_VALID, at(bus)) != 0;
    }

    /** Keeps a bus's mix in being while nothing plays through it. FMOD makes it at its next update. A bus locked already stays so. */
    static void lockBus(long bus) {
        int result = call(STUDIO_BUS_LOCK, at(bus));
        if (result != ALREADY_LOCKED) {
            check(result, "Bus_LockChannelGroup");
        }
    }

    /** A bus's mix, or 0 while FMOD has not yet made it. */
    static long busGroup(long bus) {
        MemorySegment scratch = SCRATCH.get();
        int result = call(STUDIO_BUS_GROUP, at(bus), scratch);
        if (result == NOT_LOADED) {
            return 0L;
        }
        check(result, "Bus_GetChannelGroup");
        return pointerOut(scratch);
    }

    static long groupSystem(long group) {
        MemorySegment scratch = SCRATCH.get();
        check(call(GROUP_SYSTEM, at(group), scratch), "ChannelGroup_GetSystemObject");
        return pointerOut(scratch);
    }

    static int addEffect(long group, int index, long dsp) {
        return call(GROUP_ADD, at(group), index, at(dsp));
    }

    static long groupEffect(long group, int index) {
        MemorySegment scratch = SCRATCH.get();
        check(call(GROUP_DSP, at(group), index, scratch), "ChannelGroup_GetDSP");
        return pointerOut(scratch);
    }

    static int groupEffects(long group) {
        MemorySegment scratch = SCRATCH.get();
        check(call(GROUP_DSPS, at(group), scratch), "ChannelGroup_GetNumDSPs");
        return scratch.get(INT_LAYOUT, 0);
    }

    /** A new effect of an FMOD_DSP_TYPE, or 0 with the FMOD_RESULT in {@code result[0]} where FMOD has no such type. */
    static long createEffect(long system, int type, int[] result) {
        MemorySegment scratch = SCRATCH.get();
        result[0] = call(SYSTEM_CREATE_DSP, at(system), type, scratch);
        return result[0] == OK ? pointerOut(scratch) : 0L;
    }

    static String effectName(long dsp) {
        MemorySegment scratch = SCRATCH.get();
        MemorySegment name = scratch.asSlice(64, 32);
        check(call(DSP_INFO, at(dsp), name, scratch, scratch.asSlice(4), scratch.asSlice(8), scratch.asSlice(12)), "DSP_GetInfo");
        return name.getString(0, StandardCharsets.UTF_8);
    }

    static int parameterCount(long dsp) {
        MemorySegment scratch = SCRATCH.get();
        check(call(DSP_PARAMETERS, at(dsp), scratch), "DSP_GetNumParameters");
        return scratch.get(INT_LAYOUT, 0);
    }

    /** A parameter's name, as FMOD_DSP_PARAMETER_DESC holds it after its type. */
    static String parameterName(long dsp, int index) {
        MemorySegment scratch = SCRATCH.get();
        check(call(DSP_PARAMETER_INFO, at(dsp), index, scratch), "DSP_GetParameterInfo");
        MemorySegment description = MemorySegment.ofAddress(pointerOut(scratch)).reinterpret(36);
        return description.asSlice(4, 16).getString(0, StandardCharsets.UTF_8);
    }

    /** A parameter's FMOD_DSP_PARAMETER_TYPE. */
    static int parameterType(long dsp, int index) {
        MemorySegment scratch = SCRATCH.get();
        check(call(DSP_PARAMETER_INFO, at(dsp), index, scratch), "DSP_GetParameterInfo");
        return MemorySegment.ofAddress(pointerOut(scratch)).reinterpret(4).get(INT_LAYOUT, 0);
    }

    /** The index of the parameter of this name, or throws: what an effect takes is found by name, not counted on. */
    static int parameter(long dsp, String name, int type) {
        for (int i = 0, count = parameterCount(dsp); i < count; i++) {
            if (parameterName(dsp, i).equals(name)) {
                if (parameterType(dsp, i) != type) {
                    throw new IllegalStateException("FMOD effect " + effectName(dsp) + " has " + name + " of type " + parameterType(dsp, i) + ", not " + type);
                }
                return i;
            }
        }
        throw new IllegalStateException("FMOD effect " + effectName(dsp) + " has no parameter " + name);
    }

    /**
     * The value of an int parameter FMOD names so, from FMOD_DSP_PARAMETER_DESC: its least
     * value at 48 and the names of its values at 64, from the least up.
     */
    static int valueOf(long dsp, int index, String name) {
        MemorySegment scratch = SCRATCH.get();
        check(call(DSP_PARAMETER_INFO, at(dsp), index, scratch), "DSP_GetParameterInfo");
        MemorySegment description = MemorySegment.ofAddress(pointerOut(scratch)).reinterpret(72);
        int least = description.get(INT_LAYOUT, 48), most = description.get(INT_LAYOUT, 52);
        long names = description.get(PTR, 64).address();
        if (names == 0L) {
            throw new IllegalStateException("FMOD effect " + effectName(dsp) + " names no values of " + parameterName(dsp, index));
        }
        MemorySegment list = MemorySegment.ofAddress(names).reinterpret((most - least + 1) * PTR.byteSize());
        for (int value = least; value <= most; value++) {
            if (list.getAtIndex(PTR, value - least).reinterpret(64).getString(0, StandardCharsets.UTF_8).equals(name)) {
                return value;
            }
        }
        throw new IllegalStateException("FMOD effect " + effectName(dsp) + " has no value " + name + " of " + parameterName(dsp, index));
    }

    static void setFloat(long dsp, int index, float value) {
        check(call(DSP_SET_FLOAT, at(dsp), index, value), "DSP_SetParameterFloat");
    }

    static void setInt(long dsp, int index, int value) {
        check(call(DSP_SET_INT, at(dsp), index, value), "DSP_SetParameterInt");
    }

    /** A data parameter's bytes as FMOD holds them now, valid until the effect next runs. */
    static MemorySegment data(long dsp, int index) {
        MemorySegment scratch = SCRATCH.get();
        check(call(DSP_DATA, at(dsp), index, scratch, scratch.asSlice(8), MemorySegment.NULL, 0), "DSP_GetParameterData");
        return MemorySegment.ofAddress(pointerOut(scratch)).reinterpret(scratch.get(INT_LAYOUT, 8));
    }

    static void bypass(long dsp, boolean bypassed) {
        check(call(DSP_BYPASS, at(dsp), bypassed ? 1 : 0), "DSP_SetBypass");
    }

    static void meter(long dsp) {
        check(call(DSP_METERING, at(dsp), 0, 1), "DSP_SetMeteringEnabled");
    }

    /**
     * The loudest channel's RMS level of what an effect put out in the mix block FMOD last
     * ran, from FMOD_DSP_METERING_INFO: numsamples, peaklevel[32], rmslevel[32], numchannels.
     */
    static float rms(long dsp) {
        MemorySegment scratch = SCRATCH.get();
        check(call(DSP_METERING_INFO, at(dsp), MemorySegment.NULL, scratch), "DSP_GetMeteringInfo");
        int channels = scratch.get(ValueLayout.JAVA_SHORT, 260);
        float loudest = 0f;
        for (int i = 0; i < channels; i++) {
            loudest = Math.max(loudest, scratch.get(FLOAT_LAYOUT, 132 + i * 4));
        }
        return loudest;
    }

    /** Takes an effect out of whatever mix it is in, if any. */
    static void disconnect(long dsp) {
        check(call(DSP_DISCONNECT, at(dsp), 1, 1), "DSP_DisconnectAll");
    }

    /** Gives an effect back. One still in a mix is taken out of it first. */
    static void release(long dsp) {
        int result = call(DSP_RELEASE, at(dsp));
        if (result == 8) {
            // FMOD_ERR_DSP_INUSE: still connected to the mix it was put in.
            check(call(DSP_DISCONNECT, at(dsp), 1, 1), "DSP_DisconnectAll");
            result = call(DSP_RELEASE, at(dsp));
        }
        check(result, "DSP_Release");
    }
}
