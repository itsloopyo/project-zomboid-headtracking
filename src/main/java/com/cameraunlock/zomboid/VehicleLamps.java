package com.cameraunlock.zomboid;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The lamps of a vehicle that are on, as the picture shows them: lit, whatever light the
 * vehicle stands in.
 *
 * The picture draws a vehicle through the game's own shaders in full light and lights the
 * result itself. The game's shader paints a lamp that is on over the body after its lighting,
 * from the vehicle's lights texture, so a lit lamp came into the picture as the colour of its
 * lens and was then lit as paint: black at night. So the game's vehicle shaders are rewritten as
 * they are read, to write that lamp a second time into a picture of their own (out 1), with how
 * much of the pixel is lamp; the wheels' and the wireframe's write nothing there, as they are
 * drawn with it bound too. The picture adds it after its lighting, as light.
 *
 * A vehicle shader of the game's that is not as expected is left as it is, said in the log, and
 * no lamp is drawn: the second picture would get whatever such a shader writes.
 */
final class VehicleLamps {
    private VehicleLamps() {}

    /** The bodies, each ending with the line LIT rewrites. */
    private static final Set<String> BODIES = Set.of("vehicle.frag", "vehicle_multiuv.frag", "vehicle_multiuv_noreflect.frag",
            "vehicle_norandom_multiuv.frag", "vehicle_norandom_multiuv_noreflect.frag", "vehicle_noreflect.frag");
    /** What else is drawn of a vehicle, each with one write of its colour. */
    private static final Set<String> OTHERS = Set.of("vehiclewheel.frag", "vehicle_wireframe.frag", "vehicle_wireframe_static.frag");
    private static final Pattern LIT = Pattern.compile("gl_FragColor\\s*=\\s*vec4\\(\\s*col\\s*,\\s*TexturePainColor\\.a\\s*\\)\\s*;");
    private static final Pattern WRITE = Pattern.compile("gl_FragColor\\s*=\\s*([^;]*);");

    /** How bright a lit lens is in the picture's measure: past white, so it glows. */
    static final float SHINES = 5f;

    private static volatile String refused;

    /** The source of a shader of the game's as it was read from fileName, rewritten if it is a vehicle's. */
    static String source(String code, String fileName) {
        if (code == null) {
            return null;
        }
        String name = fileName.substring(Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\')) + 1);
        boolean body = BODIES.contains(name);
        if (!body && !OTHERS.contains(name)) {
            return code;
        }
        Matcher matcher = (body ? LIT : WRITE).matcher(code);
        if (!matcher.find() || WRITE.matcher(code).results().count() != 1) {
            refused = name;
            Log.info("vehicle lamps are not drawn lit: the game's " + name + " does not end with the one colour it is expected to write");
            return code;
        }
        String out = body
                ? "gl_FragData[0] = vec4(col, TexturePainColor.a);\n    gl_FragData[1] = vec4(texColorLights.xyz, texColorLights.a * t1en * TexturePainColor.a);"
                : "gl_FragData[0] = " + matcher.group(1) + ";\n    gl_FragData[1] = vec4(0.0);";
        return code.substring(0, matcher.start()) + out + code.substring(matcher.end());
    }

    /** Whether a vehicle drawn now writes its lamps, and nothing else, into the second picture. */
    static boolean drawn() {
        return refused == null && Part.FIRST_PERSON.atStart() && Agent.lampsGap() == null;
    }

    /**
     * For the people's light: the light of the lamps that are on at a place of the picture, out of
     * the picture of them (black where it has none).
     */
    static final String GLSL = String.format(Locale.ROOT, "const float LAMP_SHINES = %.3f;%n", SHINES) + """
            uniform sampler2D uLampsLit;

            vec3 lampLight(vec2 uv) {
                vec4 lamp = texture2D(uLampsLit, uv);
                return pow(lamp.rgb, vec3(2.2)) * (lamp.a * LAMP_SHINES);
            }
            """;
}
