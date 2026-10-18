package com.cameraunlock.zomboid;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Tests of the snow vehicles and people carry: FigureSnow's sums, and the shaders' text, since nothing here runs a shader. */
final class FigureSnowTests {
    private FigureSnowTests() {}

    static void run() {
        Checks.run("aCarFirstSeenHasStoodThroughTheSnowThereIs", FigureSnowTests::aCarFirstSeenHasStoodThroughTheSnowThereIs);
        Checks.run("aParkedCarGathersSnowOnlyWhileItFallsAndNeverMoreThanLies", FigureSnowTests::aParkedCarGathersSnowOnlyWhileItFallsAndNeverMoreThanLies);
        Checks.run("aRunningCarClearsItsScreenAndBonnetAndKeepsItsRoofAWhile", FigureSnowTests::aRunningCarClearsItsScreenAndBonnetAndKeepsItsRoofAWhile);
        Checks.run("whatACarCarriesIsTheSameAtAnyFrameRate", FigureSnowTests::whatACarCarriesIsTheSameAtAnyFrameRate);
        Checks.run("aPersonGathersADustingInFallingSnowAndShedsItIndoors", FigureSnowTests::aPersonGathersADustingInFallingSnowAndShedsItIndoors);
        Checks.run("snowLiesOnlyOnTheUpperFacesOfPeople", FigureSnowTests::snowLiesOnlyOnTheUpperFacesOfPeople);
        Checks.run("snowLiesOnTheTopsOfVehiclesAndNotTheirLacquer", FigureSnowTests::snowLiesOnTheTopsOfVehiclesAndNotTheirLacquer);
        Checks.run("lowDrawsVehiclesAndPeopleWithoutSnow", FigureSnowTests::lowDrawsVehiclesAndPeopleWithoutSnow);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static float[] car(float roof, float bonnet, float screen, float heat) {
        float[] car = new float[FigureSnow.FLOATS];
        car[FigureSnow.ROOF] = roof;
        car[FigureSnow.BONNET] = bonnet;
        car[FigureSnow.SCREEN] = screen;
        car[FigureSnow.HEAT] = heat;
        return car;
    }

    /** Steps a car on for hours of game time in steps of step. */
    private static void drive(float[] car, float lying, float snowing, boolean open, boolean running, float speed, float hours, float step) {
        for (float done = 0f; done < hours - step * 0.5f; done += step) {
            FigureSnow.carOn(car, lying, snowing, open, running, speed, step);
        }
    }

    private static void aCarFirstSeenHasStoodThroughTheSnowThereIs() {
        float[] parked = new float[FigureSnow.FLOATS];
        FigureSnow.carSeen(parked, 0.7f, true, false);
        check(parked[FigureSnow.ROOF] == 0.7f && parked[FigureSnow.BONNET] == 0.7f && parked[FigureSnow.SCREEN] == 0.7f && parked[FigureSnow.HEAT] == 0f,
                "a parked car out of doors carries what lies on the ground, all over its top");
        float[] running = new float[FigureSnow.FLOATS];
        FigureSnow.carSeen(running, 0.7f, true, true);
        check(running[FigureSnow.ROOF] == 0.7f && running[FigureSnow.BONNET] == 0f && running[FigureSnow.SCREEN] == 0f && running[FigureSnow.HEAT] == 1f,
                "one first seen running has a warm engine, a clear bonnet and a wiped screen, and its roof's snow");
        float[] housed = new float[FigureSnow.FLOATS];
        FigureSnow.carSeen(housed, 0.7f, false, false);
        check(housed[FigureSnow.ROOF] == 0f && housed[FigureSnow.BONNET] == 0f && housed[FigureSnow.SCREEN] == 0f, "one under a roof carries none");
        float[] summer = new float[FigureSnow.FLOATS];
        FigureSnow.carSeen(summer, 0f, true, false);
        check(summer[FigureSnow.ROOF] == 0f, "with none lying, none");
    }

    private static void aParkedCarGathersSnowOnlyWhileItFallsAndNeverMoreThanLies() {
        float[] parked = car(0f, 0f, 0f, 0f);
        drive(parked, 0.8f, 0f, true, false, 0f, 5f, 0.01f);
        check(parked[FigureSnow.ROOF] == 0f && parked[FigureSnow.BONNET] == 0f, "a car driven clear and parked gathers nothing while none falls, however much lies");
        drive(parked, 0.8f, 1f, true, false, 0f, FigureSnow.CAR_GATHERS, 0.01f);
        float expected = 0.8f * (1f - (float) Math.exp(-1.0));
        check(Math.abs(parked[FigureSnow.ROOF] - expected) < 1e-3f && Math.abs(parked[FigureSnow.SCREEN] - expected) < 1e-3f,
                "in a full fall it gathers most of what lies over CAR_GATHERS hours, screen and all: " + parked[FigureSnow.ROOF]);
        drive(parked, 0.8f, 1f, true, false, 0f, 40f, 0.05f);
        check(parked[FigureSnow.ROOF] <= 0.8f && parked[FigureSnow.ROOF] > 0.79f, "and never more than lies on the ground");
        float[] lightFall = car(0f, 0f, 0f, 0f);
        drive(lightFall, 0.8f, 0.25f, true, false, 0f, FigureSnow.CAR_GATHERS, 0.01f);
        check(lightFall[FigureSnow.ROOF] > 0f && lightFall[FigureSnow.ROOF] < 0.4f * expected, "a light fall gathers on it more slowly: " + lightFall[FigureSnow.ROOF]);
        float[] housed = car(0.3f, 0.3f, 0.3f, 0f);
        drive(housed, 0.8f, 1f, false, false, 0f, 3f, 0.01f);
        check(housed[FigureSnow.ROOF] == 0.3f, "under a roof it gathers none and keeps what it brought in");
        drive(parked, 0.2f, 0f, true, false, 0f, 0.01f, 0.01f);
        check(parked[FigureSnow.ROOF] <= 0.2f && parked[FigureSnow.BONNET] <= 0.2f && parked[FigureSnow.SCREEN] <= 0.2f, "as the ground's snow thaws, so does a car's");
        drive(parked, 0f, 0f, true, false, 0f, 0.01f, 0.01f);
        check(parked[FigureSnow.ROOF] == 0f && parked[FigureSnow.BONNET] == 0f && parked[FigureSnow.SCREEN] == 0f, "with the ground bare, the car is bare");
    }

    private static void aRunningCarClearsItsScreenAndBonnetAndKeepsItsRoofAWhile() {
        float[] idling = car(0.8f, 0.8f, 0.8f, 0f);
        drive(idling, 0.8f, 0f, true, true, 0f, 0.1f, 0.001f);
        check(idling[FigureSnow.SCREEN] < 0.01f, "the wipers clear the screen within a few game minutes: " + idling[FigureSnow.SCREEN]);
        check(idling[FigureSnow.BONNET] > 0.3f, "the bonnet is still white while the engine warms: " + idling[FigureSnow.BONNET]);
        drive(idling, 0.8f, 0f, true, true, 0f, 0.4f, 0.001f);
        check(idling[FigureSnow.BONNET] == 0f, "half a game hour of a running engine melts the bonnet clear");
        check(idling[FigureSnow.ROOF] == 0.8f, "standing still, the roof keeps all its snow");
        float[] driven = car(0.8f, 0.8f, 0.8f, 0f);
        drive(driven, 0.8f, 0f, true, true, 60f, 0.5f, 0.001f);
        check(driven[FigureSnow.ROOF] > 0.4f && driven[FigureSnow.ROOF] < 0.7f, "at speed the roof loses its snow, but slowly: " + driven[FigureSnow.ROOF]);
        drive(driven, 0.8f, 1f, true, true, 60f, 0.5f, 0.001f);
        check(driven[FigureSnow.BONNET] == 0f && driven[FigureSnow.SCREEN] == 0f, "snow falling on a warm bonnet and a wiped screen does not stay");
        drive(driven, 0.8f, 0f, true, false, 0f, 2f, 0.01f);
        check(driven[FigureSnow.BONNET] == 0f && driven[FigureSnow.HEAT] < 0.2f, "parked again with none falling the engine cools and the bonnet stays clear");
        drive(driven, 0.8f, 1f, true, false, 0f, 0.5f, 0.001f);
        check(driven[FigureSnow.BONNET] > 0f && driven[FigureSnow.BONNET] < 0.4f, "once cold the bonnet gathers again, and not at once the full depth: " + driven[FigureSnow.BONNET]);
    }

    private static void whatACarCarriesIsTheSameAtAnyFrameRate() {
        // A frame is a game second or so at the game's usual day length.
        float[] slow = car(0.8f, 0.8f, 0.8f, 0f), fast = car(0.8f, 0.8f, 0.8f, 0f);
        drive(slow, 0.8f, 0.5f, true, true, 50f, 0.3f, 1f / 1800f);
        drive(fast, 0.8f, 0.5f, true, true, 50f, 0.3f, 1f / 8640f);
        for (int i = 0; i < FigureSnow.FLOATS; i++) {
            check(Math.abs(slow[i] - fast[i]) < 0.01f, "the same at 30 and 144 frames a game second: " + i + " " + slow[i] + " " + fast[i]);
        }
        float[] once = car(0f, 0f, 0f, 0f), often = car(0f, 0f, 0f, 0f);
        FigureSnow.carOn(once, 0.6f, 0.7f, true, false, 0f, 1f);
        drive(often, 0.6f, 0.7f, true, false, 0f, 1f, 0.001f);
        check(Math.abs(once[FigureSnow.ROOF] - often[FigureSnow.ROOF]) < 1e-3f, "a parked car's roof gathers exactly alike in one step or a thousand");
        float[] still = car(0.5f, 0.4f, 0.3f, 0.6f);
        FigureSnow.carOn(still, 0.8f, 1f, true, false, 0f, 0f);
        check(still[FigureSnow.ROOF] == 0.5f && still[FigureSnow.BONNET] == 0.4f && still[FigureSnow.SCREEN] == 0.3f && still[FigureSnow.HEAT] == 0.6f,
                "asked twice in the same moment, nothing changes");
    }

    private static void aPersonGathersADustingInFallingSnowAndShedsItIndoors() {
        check(FigureSnow.personSeen(true, true, 0.6f, 0.9f) == 0.6f, "a zombie first seen out in a fall carries what the fall gives");
        check(Math.abs(FigureSnow.personSeen(true, true, 0f, 0.9f) - FigureSnow.OUT_ALWAYS_KEEPS * 0.9f) < 1e-6f, "and a little of what lies when none falls");
        check(FigureSnow.personSeen(true, false, 0.6f, 0.9f) == 0f, "none indoors");
        check(FigureSnow.personSeen(false, true, 0.6f, 0.9f) == 0f, "a player has just stepped out");
        float dust = 0f;
        for (int step = 0; step < 100; step++) {
            dust = FigureSnow.personOn(dust, 0.8f, 0.5f, true, FigureSnow.PERSON_GATHERS / 100f);
        }
        check(Math.abs(dust - 0.8f * (1f - (float) Math.exp(-1.0))) < 1e-3f, "out in a fall a person gathers most of a dusting over PERSON_GATHERS hours: " + dust);
        check(FigureSnow.personOn(0.3f, 0f, 0.5f, true, 0f) == 0.3f, "no time, no change");
        float outside = FigureSnow.personOn(0.6f, 0f, 0.8f, true, 0.3f), indoors = FigureSnow.personOn(0.6f, 0f, 0.8f, false, 0.3f);
        check(outside > 0.45f && indoors < 0.06f, "once the fall stops it is shed slowly out of doors and soon indoors: " + outside + " " + indoors);
        check(FigureSnow.personOn(0.9f, 0.4f, 0.1f, true, 5f) == 0.4f, "in a lighter fall it sheds down to what that fall gives and no further");
        check(FigureSnow.personOn(0.5f, 1f, 0.8f, false, 1f) < 0.5f, "indoors none falls on them");
        check(FigureSnow.personOn(0.6f, 0f, 0f, true, 0.001f) == 0f, "with none falling and none lying it is too warm for any to stay");
        check(FigureSnow.personOn(0.6f, 0f, 0.2f, true, 0.001f) <= 0.2f, "nor more than lies once the fall is over");
    }

    /**
     * Snow on people is laid by snowOnPerson where the surface faces up, by the world's up as
     * the view sees it, at a place fixed on the body; it is never more than a dusting and none
     * where none has settled. CharacterDraw's people, the hands on a weapon and the mod's own arms take it.
     */
    private static void snowLiesOnlyOnTheUpperFacesOfPeople() {
        String people = SnowShader.ON_PEOPLE.replaceAll("\\s+", "");
        check(people.contains("if(uSnowOn<=0.0){return0.0;}"), "none on a person who carries none");
        check(people.contains("floattop=smoothstep(0.35,0.85,up);"), "only what faces up takes it");
        check(people.contains("returnSNOW_DUSTING*top*"), "no more than a dusting");
        check(SnowShader.DUSTING > 0.3f && SnowShader.DUSTING < 1f, "a dusting leaves what is under it showing");
        String characters = source("CharacterDraw.java").replaceAll("\\s+", "");
        check(characters.contains("floatsnowLies=snowOnPerson(dot(normalize(mat3(uModelView)*n),uUpSeen),vRest.xz);"),
                "a person's snow goes by the way the surface faces as made, not as its grain tilts it, and stays where it settled on the body");
        check(characters.contains("+GRAIN+SnowShader.ON_PEOPLE+"), "the program that draws people has it");
        check(characters.contains("upSeen[0]=view[8];upSeen[1]=view[9];upSeen[2]=view[10];"), "the world's up is the view's third column");
        String arms = source("OwnArms.java").replaceAll("\\s+", "");
        check(count(arms, "floatsnowLies=snowOnPerson(dot(n,uUpSeen),vRest.xz);") == 2, "the mod's own arms take it on sleeve and on skin");
        String drawing = source("Characters.java").replaceAll("\\s+", "");
        check(drawing.contains("if(!casting){CharacterDraw.snowed(SnowCarried.on(view.index,data.character));}"), "everyone drawn into a picture is told what they carry, and no shadow");
        check(count(drawing, "OwnArms.snowed(SnowCarried.on(view.index,data.character));") == 2, "so are the viewer's own arms, with a weapon and at a task");
        check(count(drawing, "CharacterDraw.looking(eyeView);") == 5, "every view people are drawn through says which way is up");
    }

    /**
     * Snow on a vehicle is laid in the light of the figures by its record from Characters: on
     * what faces up of its top and not below its sills, the bonnet and the screen by their own
     * share, and where it lies the paint's lacquer is under it. The bonnet seen from the seat
     * takes the bonnet's share.
     */
    private static void snowLiesOnTheTopsOfVehiclesAndNotTheirLacquer() {
        String cars = SnowShader.ON_VEHICLES.replaceAll("\\s+", "");
        check(cars.contains("floatholds=smoothstep(0.55-0.25*lain,0.85-0.15*lain,up);"), "it holds on what is nearly level, and on steeper panels the more has lain");
        check(cars.contains("returnsnowOnPanel(n.z,vec2(along,aside),lain)*smoothstep(0.4,0.5,high);"), "none on the wheels and the sills, and placed on the vehicle so it goes with it");
        check(cars.contains("floatlain=mix(mix(carried.x,carried.y,front),carried.z,screen);"), "roof, bonnet and screen each carry their own");
        check(cars.contains("floatfront=smoothstep(0.05,0.3,along/halfLength)*(1.0-roof);"), "the bonnet is ahead of the roof and below it");
        check(cars.contains("if(lain<=0.0){return0.0;}"), "none where none has lain");
        String main = PipelineShaders.FIGURE_LIGHT_MAIN.replaceAll("\\s+", "");
        check(main.contains("}elseif(uPainted>0.5&&cabin<0.5){snowLies=snowOnVehicle(at,n);}"), "only vehicles' paint seen from outside takes it");
        check(main.contains("floatlacquer=uPainted*(1.0-roofed)*uShines*(1.0-snowLies);"), "snow does not shine like lacquer");
        check(main.contains("gl_FragData[0]=vec4(color,1.0-0.5*lacquered*uShines);"), "nor does a snowy bonnet mirror the world");
        check(Characters.CAR_FLOATS == 16 && SnowShader.MOST_CARS == 16, "a vehicle's record is four vec4s, and sixteen at most");
    }

    /** In Low vehicles and people are the game's, with no snow on them: SnowCarried gives nothing there, which the shaders take as none. */
    private static void lowDrawsVehiclesAndPeopleWithoutSnow() {
        String carried = source("SnowCarried.java").replaceAll("\\s+", "");
        check(carried.contains("returnQuality.plain(view)?NONE:kept.car;") && carried.contains("returnQuality.plain(view)?0f:kept.dust;"), "nothing in Low");
        String pipeline = source("Pipeline.java").replaceAll("\\s+", "");
        check(pipeline.contains("setCars(false,seated&&view.ride.sat!=null?SnowCarried.on(view.index,view.ride.sat)[FigureSnow.BONNET]:0f);"),
                "the bonnet seen from the seat carries what SnowCarried says, and nothing in Low");
    }

    private static int count(String text, String of) {
        int found = 0;
        for (int at = text.indexOf(of); at >= 0; at = text.indexOf(of, at + 1)) {
            found++;
        }
        return found;
    }

    private static String source(String file) {
        try {
            return Files.readString(Path.of("src/main/java/com/cameraunlock/zomboid", file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
