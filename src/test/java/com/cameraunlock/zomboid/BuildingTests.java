package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Tests of what makes a building one thing: its walls' finish, its roof's planes, and what hangs along its walls. */
final class BuildingTests {
    private BuildingTests() {}

    static void run() {
        Checks.run("plainWallHasAPlinthAndACourseAndThePlinthStopsAtADoor", BuildingTests::plainWallHasAPlinthAndACourseAndThePlinthStopsAtADoor);
        Checks.run("coursesStandOnTheirWallNotASquareOut", BuildingTests::coursesStandOnTheirWallNotASquareOut);
        Checks.run("canopyIsLinedWithPanelsAndALightEveryThirdSquare", BuildingTests::canopyIsLinedWithPanelsAndALightEveryThirdSquare);
        Checks.run("finishIsWhatMostWallsShow", BuildingTests::finishIsWhatMostWallsShow);
        Checks.run("roomShowsOutsideWhatItsOwnWallsDoOrTheNearestSeen", BuildingTests::roomShowsOutsideWhatItsOwnWallsDoOrTheNearestSeen);
        Checks.run("sheetmateIsNamedByItsPlaceOnTheSheet", BuildingTests::sheetmateIsNamedByItsPlaceOnTheSheet);
        Checks.run("slopeOfUnevenTilesIsOnePlane", BuildingTests::slopeOfUnevenTilesIsOnePlane);
        Checks.run("twoSlopesMeetAtTheirRidge", BuildingTests::twoSlopesMeetAtTheirRidge);
        Checks.run("slopeClimbsFromOneLevelIntoTheNext", BuildingTests::slopeClimbsFromOneLevelIntoTheNext);
        Checks.run("lowerRoofBesideAHigherOneKeepsItsOwnPlane", BuildingTests::lowerRoofBesideAHigherOneKeepsItsOwnPlane);
        Checks.run("tileFarFromItsSlopeLeavesTheSlopeAsRead", BuildingTests::tileFarFromItsSlopeLeavesTheSlopeAsRead);
        Checks.run("shallowRoofKeepsItsOwnPitch", BuildingTests::shallowRoofKeepsItsOwnPitch);
        Checks.run("unclearTileTakesThePlaneOfItsRow", BuildingTests::unclearTileTakesThePlaneOfItsRow);
        Checks.run("unclearTileWhereTwoSlopesMeetIsLeftAlone", BuildingTests::unclearTileWhereTwoSlopesMeetIsLeftAlone);
        Checks.run("pipeAlongAWallMeetsAcrossTwoSprites", BuildingTests::pipeAlongAWallMeetsAcrossTwoSprites);
        Checks.run("wallTopTakesItsTexelsFromBelowTheSeal", BuildingTests::wallTopTakesItsTexelsFromBelowTheSeal);
        Checks.run("farSlopeIsTheNearOneOverTheRidge", BuildingTests::farSlopeIsTheNearOneOverTheRidge);
        Checks.run("farSlopeStartsPastARoofsLevelTop", BuildingTests::farSlopeStartsPastARoofsLevelTop);
        Checks.run("farSlopeStopsWhereTheBuildingDoesAndAtAnUpperStorey", BuildingTests::farSlopeStopsWhereTheBuildingDoesAndAtAnUpperStorey);
        Checks.run("roofWithBothItsSlopesGetsNoMore", BuildingTests::roofWithBothItsSlopesGetsNoMore);
        Checks.run("pavementIsNotLooseStone", BuildingTests::pavementIsNotLooseStone);
        Checks.run("skinsOfTwoWallsMeetOnTheOutsideOfEveryCorner", BuildingTests::skinsOfTwoWallsMeetOnTheOutsideOfEveryCorner);
        Checks.run("wallThatEndsAloneIsClosedAcrossItsEnd", BuildingTests::wallThatEndsAloneIsClosedAcrossItsEnd);
        Checks.run("eavesMeetAtAHipAndRunOutOverAGable", BuildingTests::eavesMeetAtAHipAndRunOutOverAGable);
        Checks.run("aDownpipeRunsDownAWallAndNeverStandsAlone", BuildingTests::aDownpipeRunsDownAWallAndNeverStandsAlone);
        Checks.run("openGroundHasTheLightMostOfItHas", BuildingTests::openGroundHasTheLightMostOfItHas);
    }

    private static void plainWallHasAPlinthAndACourseAndThePlinthStopsAtADoor() {
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        Courses.add(mesh, 4f, 9f, 0.5f, 5f, 9f, 0.5f, 0f, -1f, -1f, -1f, 1f, 0.5f, 0.25f);
        check(mesh.triangles() == 8, "a plain wall has a plinth and a course, each a face and a ledge: " + mesh.triangles());
        float lowest = Float.MAX_VALUE, highest = 0f;
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            float y = mesh.vertices[at + 1], up = mesh.vertices[at + 2];
            check(y <= 9f && y >= 9f - Courses.PROUD - 1e-5f && mesh.vertices[at] >= 4f && mesh.vertices[at] <= 5f, "on the side asked for, as long as the wall");
            check(up <= 0.5f + Courses.PLINTH + 1e-5f || up >= 0.5f + ViewCamera.LEVEL_HEIGHT - Courses.COURSE - 1e-5f, "one at the wall's foot and one under its top");
            check(mesh.vertices[at + 5] < 1f && mesh.vertices[at + 5] > 0.7f && near(mesh.vertices[at + 6] * 2f, mesh.vertices[at + 5]), "a shade darker than the wall, in its colour");
            lowest = Math.min(lowest, up);
            highest = Math.max(highest, up);
        }
        check(near(lowest, 0.5f) && near(highest, 0.5f + ViewCamera.LEVEL_HEIGHT), "from the ground the wall stands on to its top");
        mesh.clear();
        Courses.add(mesh, 4f, 9f, 0f, 5f, 9f, 0f, 0f, -1f, 0.3f, 0.7f, 1f, 1f, 1f);
        check(mesh.triangles() == 12, "at a doorway the plinth is two lengths");
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            float x = mesh.vertices[at], up = mesh.vertices[at + 2];
            check(up > 1f || x <= 4.3f + 1e-5f || x >= 4.7f - 1e-5f, "and none of it crosses the doorway");
        }
    }

    private static void coursesStandOnTheirWallNotASquareOut() {
        // The north wall of square (4, 9), out of doors to the north, then its west wall, out of
        // doors to the west, as WallBuilder.standEdge hands them to addCourses.
        TestMap map = new TestMap();
        TestMap.write(map.build.wallBuilder, "farColour", new float[] {1f, 1f, 1f});
        float[] noOpening = new float[14];
        Arrays.fill(noOpening, -1f);
        map.build.wallBuilder.addCourses(noOpening, 0, 4, 9, 0, 0f, 0f, 1f, 0f, 0f, -1f);
        float[] box = bounds(TestMap.plain());
        check(TestMap.plain().triangles() > 0 && box[0] >= 4f - 1e-5f && box[1] <= 5f + 1e-5f && box[2] >= 9f - Courses.PROUD - 1e-5f && box[3] <= 9f + 1e-5f,
                "a north wall's plinth and course stand on its edge, proud of it to the north: x " + box[0] + " to " + box[1] + ", y " + box[2] + " to " + box[3]);
        TestMap.plain().clear();
        map.build.wallBuilder.addCourses(noOpening, 0, 4, 9, 0, 0f, 0f, 0f, 1f, -1f, 0f);
        box = bounds(TestMap.plain());
        check(TestMap.plain().triangles() > 0 && box[0] >= 4f - Courses.PROUD - 1e-5f && box[1] <= 4f + 1e-5f && box[2] >= 9f - 1e-5f && box[3] <= 10f + 1e-5f,
                "a west wall's plinth and course stand on its edge, proud of it to the west: x " + box[0] + " to " + box[1] + ", y " + box[2] + " to " + box[3]);
    }

    /** The least and most x, then the least and most y, of a mesh's vertices. */
    private static float[] bounds(SpriteGeometry.Mesh mesh) {
        float[] box = {Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            box[0] = Math.min(box[0], mesh.vertices[at]);
            box[1] = Math.max(box[1], mesh.vertices[at]);
            box[2] = Math.min(box[2], mesh.vertices[at + 1]);
            box[3] = Math.max(box[3], mesh.vertices[at + 1]);
        }
        return box;
    }

    private static void canopyIsLinedWithPanelsAndALightEveryThirdSquare() {
        int fittings = 0;
        for (int x = -6; x < 6; x++) {
            for (int y = -6; y < 6; y++) {
                fittings += Soffits.fitted(x, y) ? 1 : 0;
            }
        }
        check(fittings == 16 && Soffits.fitted(-2, 4) && !Soffits.fitted(0, 0), "one square in nine has a light, west and north of the map's corner as east and south of it");
        float[] light = {0.5f, 0.5f, 0.5f, 0.5f};
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        Soffits.add(mesh, 7, 9, 4f, light, light, light, 288f, 448f, false, true);
        check(mesh.triangles() == 2 + 4 * 2, "a square of lining is its panel and four joints: " + mesh.triangles());
        mesh.clear();
        Soffits.add(mesh, 7, 9, 4f, light, light, light, 288f, 448f, true, true);
        check(mesh.triangles() == 4 * 2 + 4 * 2 + 2 + 4 * 2, "one with a light is the lining round the opening, the recess, the lens and the joints: " + mesh.triangles());
        int lens = 0;
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            float a = mesh.vertices[at] - 7f, b = mesh.vertices[at + 1] - 9f, up = mesh.vertices[at + 2];
            check(a >= 0f && a <= 1f && b >= 0f && b <= 1f && up >= 4f - Soffits.JOINT_PROUD - 1e-5f && up <= 4f + Soffits.RECESS + 1e-5f, "all of it on its own square, at the lining or let into it");
            check(mesh.vertices[at + 11] < 0f, "and all of it faces down");
            if (mesh.vertices[at + 8] == 448f) {
                lens++;
                check(up == 4f + Soffits.RECESS && mesh.vertices[at + 5] == 1f && Math.abs(a - 0.5f) <= Soffits.LENS_HALF + 1e-5f, "a lit lens is let in over the middle of the square and is as bright as a light is");
            } else {
                check(mesh.vertices[at + 5] <= 0.5f, "the lining has the light of what is under it");
            }
        }
        check(lens == 6, "the lens is one face");
        mesh.clear();
        Soffits.strip(mesh, 3, 5, 2.4f, true, 0.8f, 0.8f, 0.8f, 0f, 448f);
        check(mesh.triangles() == 2 * 2 + 3 * 2, "a square of strip light is two sides and an underside in three bands: " + mesh.triangles());
        int lensFloats = 0;
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            float a = mesh.vertices[at] - 3f, b = mesh.vertices[at + 1] - 5f, up = mesh.vertices[at + 2];
            check(a >= 0f && a <= 1f && Math.abs(b - 0.5f) <= Soffits.STRIP_HALF + 1e-5f && up <= 2.4f && up >= 2.4f - Soffits.STRIP_DEEP - 1e-5f,
                    "it runs the whole square along x, under the ceiling, so that the next length meets it");
            if (mesh.vertices[at + 8] == 448f) {
                lensFloats++;
                check(mesh.vertices[at + 5] == 0.8f && Math.abs(b - 0.5f) <= Soffits.STRIP_LENS_HALF + 1e-5f, "its lens has the whole of the room's light");
            }
        }
        check(lensFloats == 6, "and is one face");
        check(Soffits.under(0.1f, Soffits.GLOW_RED) == Soffits.GLOW_RED && Soffits.under(1f, Soffits.GLOW_RED) == 1f, "a lit canopy lights the ground under it at night and takes nothing from daylight");
    }

    private static void openGroundHasTheLightMostOfItHas() {
        OpenLight light = new OpenLight();
        check(light.isEmpty(), "nothing is counted to begin with");
        // A street at night: most of it in the dark, a few squares under a lamp.
        for (int i = 0; i < 90; i++) {
            light.add(0.2f, 0.2f, 0.25f);
        }
        for (int i = 0; i < 10; i++) {
            light.add(0.9f, 0.8f, 0.6f);
        }
        float[] out = new float[3];
        light.commonest(out);
        check(near(out[0], 0.2f) && near(out[1], 0.2f) && near(out[2], 0.25f),
                "an unseen square is as dark as the street is, and not the mean of the street and its lamps: " + out[0]);
        // The chunk the character stands in, a torch lit: most of what is seen of it is in the beam.
        light.clear();
        for (int i = 0; i < 12; i++) {
            light.add(0.18f, 0.18f, 0.18f);
        }
        for (int i = 0; i < 40; i++) {
            light.add(1f, 1f, 1f);
        }
        light.add(0f, 0f, 0f);
        light.floor(out);
        check(near(out[0], 0.18f), "the open ground's own light is that of the squares no lamp is on, however few they are beside the lit: " + out[0]);
        light.clear();
        for (int i = 0; i < 64; i++) {
            light.add(i / 64f, i / 64f, i / 64f);
        }
        light.floor(out);
        check(near(out[0], 0f), "and where no band holds a fair share of the squares, the fullest, of equals the darkest: " + out[0]);
        light.clear();
        check(light.isEmpty(), "and cleared it holds nothing");
        light.add(1f, 1f, 1f);
        light.commonest(out);
        check(near(out[0], 1f), "full light is counted, in the last band");
    }

    /** The walls round a set of squares: a west wall on a square whose west neighbour is not in the set, and so on round. */
    private static WallJoins.Walls wallsRound(Set<Long> squares) {
        return (x, y, north) -> squares.contains(key(x, y)) != squares.contains(north ? key(x, y - 1) : key(x - 1, y));
    }

    private static long key(int x, int y) {
        return (long) x << 32 | y & 0xffffffffL;
    }

    /** Where a skin of the wall on an edge of a square ends: into out the x and y of its start and of its end. */
    private static void skin(int x, int y, boolean north, boolean near, int joins, float half, float[] out) {
        int which = near ? WallJoins.NEAR : WallJoins.FAR;
        float before = (joins & which) != 0 ? half : 0f, after = (joins >> 3 & which) != 0 ? half : 0f;
        float side = near ? half : -half;
        if (north) {
            out[0] = x - before;
            out[1] = y + side;
            out[2] = x + 1 + after;
            out[3] = y + side;
        } else {
            out[0] = x + side;
            out[1] = y - before;
            out[2] = x + side;
            out[3] = y + 1 + after;
        }
    }

    private static void skinsOfTwoWallsMeetOnTheOutsideOfEveryCorner() {
        // An L: a block four by three with a wing two by two on its south side, so five corners point out and one in.
        Set<Long> house = new HashSet<>();
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 3; y++) {
                house.add(key(x, y));
            }
        }
        for (int x = 0; x < 2; x++) {
            for (int y = 3; y < 5; y++) {
                house.add(key(x, y));
            }
        }
        WallJoins.Walls walls = wallsRound(house);
        float half = 0.04f;
        float[] one = new float[4], other = new float[4];
        int corners = 0;
        for (int cx = -1; cx <= 5; cx++) {
            for (int cy = -1; cy <= 6; cy++) {
                // The walls that end at the grid point (cx, cy): two along x and two along y.
                boolean east = walls.stands(cx, cy, true), west = walls.stands(cx - 1, cy, true);
                boolean south = walls.stands(cx, cy, false), north = walls.stands(cx, cy - 1, false);
                int count = (east ? 1 : 0) + (west ? 1 : 0) + (south ? 1 : 0) + (north ? 1 : 0);
                if (count != 2 || east == west) {
                    continue;
                }
                corners++;
                int alongX = east ? cx : cx - 1, alongY = south ? cy : cy - 1;
                int joinsX = WallJoins.of(walls, alongX, cy, true), joinsY = WallJoins.of(walls, cx, alongY, false);
                // The skins on the side of the quarter between the two walls are the ones inside the corner.
                boolean quarterIsNearX = south, quarterIsNearY = east;
                skin(alongX, cy, true, !quarterIsNearX, joinsX, half, one);
                skin(cx, alongY, false, !quarterIsNearY, joinsY, half, other);
                float oneX = east ? one[0] : one[2], oneY = one[1], otherX = other[0], otherY = south ? other[1] : other[3];
                check(near(oneX, otherX) && near(oneY, otherY), "the two skins outside the corner at " + cx + ", " + cy + " end at one point: "
                        + oneX + ", " + oneY + " and " + otherX + ", " + otherY);
                check(near(Math.abs(oneX - cx), half) && near(Math.abs(oneY - cy), half), "which is half a wall out from the corner both ways");
                skin(alongX, cy, true, quarterIsNearX, joinsX, half, one);
                skin(cx, alongY, false, quarterIsNearY, joinsY, half, other);
                check(near(east ? one[0] : one[2], cx) && near(south ? other[1] : other[3], cy), "the two inside it stop at the corner");
            }
        }
        check(corners == 6, "an L has six corners: " + corners);
    }

    private static void wallThatEndsAloneIsClosedAcrossItsEnd() {
        check(WallJoins.end(false, false, false) == WallJoins.OPEN, "a wall with nothing at its end is closed there");
        check(WallJoins.end(true, false, false) == 0 && WallJoins.end(true, true, false) == 0, "one that runs on is not, whatever joins it");
        check(WallJoins.end(false, true, true) == 0, "nor one that ends against a wall crossing it: its end is inside that wall");
        check(WallJoins.end(false, true, false) == WallJoins.FAR && WallJoins.end(false, false, true) == WallJoins.NEAR,
                "at a corner the skin on the other side from the wall it meets runs on");
        // A wall along x with a doorway's width missing from it: both lengths end open at the gap.
        WallJoins.Walls gapped = (x, y, north) -> north && y == 0 && x >= 0 && x <= 4 && x != 2;
        check(WallJoins.of(gapped, 1, 0, true) >> 3 == WallJoins.OPEN && (WallJoins.of(gapped, 3, 0, true) & 7) == WallJoins.OPEN, "the jambs of a gap in a wall are closed");
        check((WallJoins.of(gapped, 1, 0, true) & 7) == 0 && WallJoins.of(gapped, 3, 0, true) >> 3 == 0, "and the wall runs on at its other ends");
    }

    private static void eavesMeetAtAHipAndRunOutOverAGable() {
        float eave = 0.22f, rake = 0.14f;
        int hip = Eaves.end(true, false), gable = Eaves.end(false, false), on = Eaves.end(false, true);
        check(Eaves.atWall(on, rake) == 0f && Eaves.atEdge(on, eave, rake) == 0f, "an eave that goes on in the next square stops at its own square's side");
        check(Eaves.atWall(hip, rake) == 0f && Eaves.atEdge(hip, eave, rake) == eave,
                "at a hip the eave's edge runs on as far as the other eave stands out, so the two meet on the diagonal");
        check(Eaves.atWall(gable, rake) == rake && Eaves.atEdge(gable, eave, rake) == rake, "at a gable it runs on as far as the roof's edge over the gable does");
        check(Eaves.end(true, true) == Eaves.MEETS, "two eaves of one tile meet whatever is beside it");
        Eaves.Walled wall = level -> true;
        check(Eaves.pipeFoot(1, level -> Eaves.AIR, wall) == 0f && Eaves.pipeFoot(2, level -> level == 0 ? Eaves.FLOOR : Eaves.AIR, wall) == 0f,
                "a gutter's pipe comes down open air to the ground");
        check(Eaves.pipeFoot(2, level -> level == 1 ? Eaves.FLOOR : Eaves.AIR, wall) == ViewCamera.LEVEL_HEIGHT, "or to a flat roof it meets on the way");
        check(Float.isNaN(Eaves.pipeFoot(2, level -> level == 0 ? Eaves.ROOM : Eaves.AIR, wall)) && Float.isNaN(Eaves.pipeFoot(2, level -> level == 2 ? Eaves.ROOM : Eaves.AIR, wall)),
                "and there is none where it would come down through a room: round a skylight");
        check(Eaves.pipeFoot(2, level -> level == 2 ? Eaves.FLOOR : Eaves.AIR, wall) == 2f * ViewCamera.LEVEL_HEIGHT, "an eave onto a deck at its own level has its foot there, too short to hang");
    }

    private static void aDownpipeRunsDownAWallAndNeverStandsAlone() {
        Eaves.Under open = level -> Eaves.AIR;
        check(Float.isNaN(Eaves.pipeFoot(1, open, level -> false)), "a porch roof's open corner, with no wall under its eave, has no pipe");
        check(Float.isNaN(Eaves.pipeFoot(0, open, level -> false)), "nor has an awning or canopy on the ground level standing out from its wall");
        check(Float.isNaN(Eaves.pipeFoot(0, open, level -> level == 1)), "the wall it hangs from on the ground level is the one the pipe needs, not one above");
        check(Eaves.pipeFoot(1, open, level -> level == 0) == 0f && Eaves.pipeFoot(0, open, level -> level == 0) == 0f,
                "against the wall under the eave the pipe comes down to the ground");
        check(Float.isNaN(Eaves.pipeFoot(3, open, level -> level != 0)), "an upper storey over an open ground floor leaves the pipe with nothing to run down");
        check(Float.isNaN(Eaves.pipeFoot(3, open, level -> level != 2)), "and so does a top storey set back from the wall below it");
        check(Eaves.pipeFoot(3, level -> level == 1 ? Eaves.FLOOR : Eaves.AIR, level -> level >= 1) == ViewCamera.LEVEL_HEIGHT,
                "down to a balcony only the walls above the balcony carry it");
        check(Eaves.SHOE_OUT >= 0.1f && Eaves.SHOE_OUT <= 0.15f && Eaves.SHOE_HIGH <= 0.2f, "its shoe turns out from the wall as far as a real one, 10 to 15 cm, at the foot");
        // As RoofBuilder hangs it: the eave of a tile on level 1 at (5, 4), falling south to its
        // floor, and one falling east, with a wall of the storey under it on the edge asked.
        check(pipeHung(true, 5, 5, true), "under an eave on the south edge the pipe runs down the wall on that edge, the north wall of the square past it");
        check(pipeHung(true, 4, 5, true), "or down the wall of the square before it along the eave, at the corner the pipe is on");
        check(!pipeHung(true, 5, 4, true), "and not down a wall a square in from the eave");
        check(!pipeHung(true, -1, -1, true), "with no wall under the eave there is no pipe");
        check(pipeHung(false, 6, 4, false), "under an eave on the east edge the pipe runs down the west wall of the square past it");
        check(!pipeHung(false, 5, 4, false), "and not down the west wall of the eave's own square");
    }

    /**
     * Whether RoofBuilder hangs a pipe from the eave of a tile on level 1 at (5, 4) that falls
     * south (or east) to its floor, with a wall on level 0 on the north (or west) edge of the
     * square (wallX, wallY): something of it at the foot of the storey under the eave.
     */
    private static boolean pipeHung(boolean south, int wallX, int wallY, boolean north) {
        TestMap map = new TestMap();
        if (wallX >= 0) {
            map.wall(wallX, wallY, 0, north);
        }
        SpriteGeometry.Mesh sheet = new SpriteGeometry.Mesh();
        float rise = 1f / 3f;
        map.build.roofBuilder.addEave(sheet, 5, 4, 1, rise, south ? 0f : -rise, south ? -rise : 0f, true);
        check(sheet.triangles() > 0, "the eave is made");
        SpriteGeometry.Mesh plain = TestMap.plain();
        boolean foot = false;
        for (int at = 0; at < plain.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            foot |= plain.vertices[at + 2] < 0.5f * ViewCamera.LEVEL_HEIGHT;
        }
        return foot;
    }

    private static void pavementIsNotLooseStone() {
        check(Ground.of("Road_04", "Gravel", true) == Ground.PAVING, "the pale floor of a pavement or a square is paving, whatever it sounds like underfoot");
        check(Ground.of("Road_04", null, false) == Ground.PAVING, "and so is its edging laid over the floor beside it");
        check(Ground.of(null, "Gravel", true) == Ground.GRAVEL, "a floor of gravel that is no street's is loose stone");
        check(Ground.of(null, "Gravel", false) == 0f, "what lies on a floor is not the ground");
        check(Ground.of("Road_07", null, true) == 0f && Ground.of(null, "Concrete", true) == 0f, "any other ground is what its sheet says");
    }

    /** A site with the building under squares from one x to another on rows 0 and 1, a floor at one level over a run of x, and rooms at a level from an x on. */
    private static RoofFit.Site site(int builtFrom, int builtTo, int floorLevel, int floorFrom, int floorTo, int roomLevel, int roomTo) {
        return new RoofFit.Site() {
            @Override
            public boolean stands(int x, int y, int z) {
                return x >= builtFrom && x <= builtTo && y >= 0 && y <= 1;
            }

            @Override
            public boolean floored(int x, int y, int z) {
                return z == floorLevel && x >= floorFrom && x <= floorTo;
            }

            @Override
            public boolean taken(int x, int y, int z, float roof) {
                return z == roomLevel && x <= roomTo || floored(x, y, z) || floored(x, y, z + 1);
            }
        };
    }

    /** A slope falling east from height top at the west edge of square x, a third of a level a tile, deep tiles deep, on rows 0 and 1. */
    private static List<RoofFit.Tile> eastSlope(int x, float top, int deep) {
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (int y = 0; y < 2; y++) {
            for (int i = 0; i < deep; i++) {
                float high = top - i * RoofFit.PITCH;
                tile(roof, x + i, y, (int) Math.floor(high - RoofFit.PITCH + 0.01f), RoofFit.ALONG_X, true, high, high - RoofFit.PITCH);
            }
        }
        RoofFit.fit(roof);
        return roof;
    }

    private static RoofFit.Tile at(List<RoofFit.Tile> tiles, int x, int y) {
        for (RoofFit.Tile tile : tiles) {
            if (tile.x == x && tile.y == y) {
                return tile;
            }
        }
        return null;
    }

    private static void farSlopeIsTheNearOneOverTheRidge() {
        // The map's half of a gable roof: three tiles falling east from a ridge at 2 along x = 3.
        List<RoofFit.Tile> backs = RoofFit.backs(eastSlope(3, 2f, 3), site(0, 5, -1, 0, 0, -1, 0));
        check(backs.size() == 6, "each row of the slope is carried over the ridge, three tiles a row: " + backs.size());
        RoofFit.Tile first = at(backs, 2, 0), last = at(backs, 0, 1);
        check(first != null && near(first.fitHigh, 2f) && near(first.fitLow, 2f - RoofFit.PITCH) && first.fitAxis == RoofFit.ALONG_X,
                "the far slope starts at the ridge and falls away from it");
        check(last != null && near(last.fitLow, 1f) && near(last.fitHigh, 1f + RoofFit.PITCH) && last.z == 1,
                "and comes down to the height the near slope's foot is at, on the same level");
        check(first.z == 1 && first.fitted, "its tiles are laid as fitted ones are");
    }

    private static void farSlopeStartsPastARoofsLevelTop() {
        // Two tiles of slope up to a level top that is the floor of level 2 from x = 4 to 9.
        List<RoofFit.Tile> backs = RoofFit.backs(eastSlope(10, 2f, 2), site(2, 11, 2, 4, 9, -1, 0));
        check(backs.size() == 4 && at(backs, 9, 0) == null && at(backs, 4, 0) == null, "nothing is laid over the level top: " + backs.size());
        RoofFit.Tile first = at(backs, 3, 0), last = at(backs, 2, 0);
        check(first != null && near(first.fitHigh, 2f) && near(first.fitLow, 2f - RoofFit.PITCH), "the far slope falls from the far edge of the top");
        check(last != null && near(last.fitLow, 2f - 2f * RoofFit.PITCH), "as far down as the near one");
    }

    private static void farSlopeStopsWhereTheBuildingDoesAndAtAnUpperStorey() {
        List<RoofFit.Tile> backs = RoofFit.backs(eastSlope(3, 2f, 3), site(2, 5, -1, 0, 0, -1, 0));
        check(backs.size() == 2 && at(backs, 2, 0) != null && at(backs, 1, 0) == null, "no roof is laid past the building's wall: " + backs.size());
        // A lean-to against a house a storey higher: the rooms of level 1 are where its far slope would be.
        backs = RoofFit.backs(eastSlope(3, 2f, 3), site(0, 5, -1, 0, 0, 1, 2));
        check(backs.isEmpty(), "no roof is laid through an upper storey: " + backs.size());
    }

    private static void roofWithBothItsSlopesGetsNoMore() {
        List<RoofFit.Tile> roof = eastSlope(3, 2f, 3);
        for (int y = 0; y < 2; y++) {
            for (int i = 0; i < 3; i++) {
                tile(roof, i, y, 1, RoofFit.ALONG_X, true, 1f + i * RoofFit.PITCH, 1f + (i + 1) * RoofFit.PITCH);
            }
        }
        check(RoofFit.backs(roof, site(0, 5, -1, 0, 0, -1, 0)).isEmpty(), "a roof the map has both sides of is left as it is");
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-4f;
    }

    private static void finishIsWhatMostWallsShow() {
        WallSides.Votes votes = new WallSides.Votes();
        check(votes.isEmpty() && votes.commonest() == -1, "walls that show nothing have no finish");
        for (int vote : new int[] {4, 7, 7, 2, 7, 4, 2, 2, 7}) {
            votes.add(vote);
        }
        check(!votes.isEmpty() && votes.commonest() == 7, "the finish is the sprite most of the walls show");
        WallSides.Votes tied = new WallSides.Votes();
        for (int vote : new int[] {5, 3, 3, 5}) {
            tied.add(vote);
        }
        check(tied.commonest() == 5, "of two shown equally often it is the one met first, so every wall of a building gets the same answer");
        WallSides.Votes many = new WallSides.Votes();
        for (int i = 0; i < 100; i++) {
            many.add(i % 3 == 0 ? 9 : i);
        }
        check(many.commonest() == 9, "a long wall's votes are all counted");
    }

    private static void roomShowsOutsideWhatItsOwnWallsDoOrTheNearestSeen() {
        int brick = 0, boards = 1;
        long hall = 11L, garage = 12L, corner = 13L, cellar = 14L;
        WallSides.Outsides outsides = new WallSides.Outsides();
        for (int i = 0; i < 4; i++) {
            outsides.shows(hall, brick);
            outsides.shows(garage, boards);
        }
        outsides.shows(hall, boards);
        outsides.sees(garage, 1, brick);
        outsides.sees(corner, 13, boards);
        outsides.sees(corner, 7, brick);
        outsides.sees(corner, 9, boards);
        check(outsides.of(hall) == brick, "a room shows outside what most of its own outside walls show, a chimney breast among them or not");
        check(outsides.of(garage) == boards, "a boarded garage on a brick house is boarded behind too, whatever is seen from it");
        check(outsides.of(corner) == brick, "a room with no outside wall to read shows what the nearest one seen through the building shows");
        check(outsides.of(cellar) == -1, "a room that has none and sees none has no finish of its own");
    }

    private static void sheetmateIsNamedByItsPlaceOnTheSheet() {
        check(WallSides.sheetmate("walls_exterior_house_01_17", -1).equals("walls_exterior_house_01_16"), "the wall before a north wall on its sheet is the west wall of the same finish");
        check(WallSides.sheetmate("walls_exterior_house_01_16", 1).equals("walls_exterior_house_01_17"), "and the one after a west wall the north wall");
        check(WallSides.sheetmate("walls_exterior_house_01_9", 1).equals("walls_exterior_house_01_10"), "a place is a number, not a digit");
        check(WallSides.sheetmate("carpentry_wall", -1).equals("carpentry_wall"), "a sprite not named for a place has no sheetmate");
        check(WallSides.sheetmate("odd_", 1).equals("odd_"), "nor has a name that ends at its last underscore");
        check(WallSides.sheetmate("mod_walls_99999999999", 1).equals("mod_walls_99999999999"), "nor one whose number runs past what an int holds");
        check(WorldScene.materialOf("floors_interior_tilesandwood_01_99999999999", true) == WorldScene.materialOf("floors_interior_tilesandwood_01_x", true),
                "a floor of a mod's whose number runs past what an int holds is no sprite of the tiles and wood sheet");
    }

    private static RoofFit.Tile tile(List<RoofFit.Tile> roof, int x, int y, int z, int axis, boolean sure, float low, float high) {
        RoofFit.Tile tile = new RoofFit.Tile(x, y, z, axis, sure, low, high);
        roof.add(tile);
        return tile;
    }

    private static void slopeOfUnevenTilesIsOnePlane() {
        // A slope three tiles deep and four wide, falling south from 2 to 1, each tile read a little out.
        List<RoofFit.Tile> roof = new ArrayList<>();
        float[] out = {0.04f, -0.03f, 0.05f, -0.05f, 0.02f, 0.06f, -0.04f, 0.03f, -0.02f, 0.05f, 0.01f, -0.06f};
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 4; column++) {
                float high = 2f - row / 3f, wrong = out[row * 4 + column];
                tile(roof, 10 + column, 20 + row, 1, RoofFit.ALONG_Y, true, high + wrong, high - 1f / 3f - wrong / 2f);
            }
        }
        RoofFit.fit(roof);
        for (RoofFit.Tile tile : roof) {
            check(tile.fitted, "every tile of the slope is put on its plane");
            float high = 2f - (tile.y - 20) / 3f;
            check(near(tile.fitLow, high) && near(tile.fitHigh, high - 1f / 3f), "and the plane is the game's pitch, meeting the tile edges at thirds of a level");
        }
        for (RoofFit.Tile tile : roof) {
            for (RoofFit.Tile other : roof) {
                if (other.x == tile.x && other.y == tile.y + 1) {
                    check(tile.fitHigh == other.fitLow, "a tile's far edge is at the height of the next tile's near edge: no step down the slope");
                }
                if (other.y == tile.y && other.x == tile.x + 1) {
                    check(tile.fitLow == other.fitLow && tile.fitHigh == other.fitHigh, "and tiles side by side are at one height: no step along it");
                }
            }
        }
    }

    private static void twoSlopesMeetAtTheirRidge() {
        List<RoofFit.Tile> roof = new ArrayList<>();
        // A ridge along x at y = 23: three rows climb to it from the north and three fall from it to the south.
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                tile(roof, column, 20 + row, 1, RoofFit.ALONG_Y, true, 1.03f + row / 3f, 1.36f + row / 3f);
                tile(roof, column, 23 + row, 1, RoofFit.ALONG_Y, true, 1.95f - row / 3f, 1.64f - row / 3f);
            }
        }
        RoofFit.fit(roof);
        float northAtRidge = Float.NaN, southAtRidge = Float.NaN;
        for (RoofFit.Tile tile : roof) {
            check(tile.fitted, "both slopes are fitted");
            if (tile.y == 22) {
                northAtRidge = tile.fitHigh;
            }
            if (tile.y == 23) {
                southAtRidge = tile.fitLow;
            }
        }
        check(near(northAtRidge, 2f) && northAtRidge == southAtRidge, "the two slopes come to one height along the ridge");
    }

    private static void slopeClimbsFromOneLevelIntoTheNext() {
        List<RoofFit.Tile> roof = new ArrayList<>();
        // Rising north: three tiles on level 1, then three more on level 2.
        for (int row = 0; row < 6; row++) {
            float high = 3f - row / 3f;
            tile(roof, 5, 30 + row, row < 3 ? 2 : 1, RoofFit.ALONG_Y, true, high + 0.03f, high - 1f / 3f + 0.03f);
        }
        RoofFit.fit(roof);
        for (RoofFit.Tile tile : roof) {
            float high = 3f - (tile.y - 30) / 3f;
            check(tile.fitted && near(tile.fitLow, high), "a slope that goes on in the next level is one plane across both");
        }
    }

    private static void lowerRoofBesideAHigherOneKeepsItsOwnPlane() {
        List<RoofFit.Tile> roof = new ArrayList<>();
        RoofFit.Tile[] main = new RoofFit.Tile[3], porch = new RoofFit.Tile[2];
        for (int row = 0; row < 3; row++) {
            main[row] = tile(roof, 0, row, 1, RoofFit.ALONG_Y, true, 2f - row / 3f, 2f - (row + 1) / 3f);
        }
        // A porch roof beside the house's, sloping the same way, half a level lower.
        for (int row = 0; row < 2; row++) {
            porch[row] = tile(roof, 1, 1 + row, 1, RoofFit.ALONG_Y, true, 1.16f - row * 0.3f, 0.86f - row * 0.3f);
        }
        RoofFit.fit(roof);
        check(near(main[1].fitLow, 5f / 3f) && near(main[2].fitHigh, 1f), "the house's slope is fitted by its own tiles");
        check(porch[0].fitted && Math.abs(porch[0].fitLow - 1.16f) < 0.05f && porch[0].fitHigh == porch[1].fitLow,
                "and the porch's by its own: it is not pulled up into the house's plane");
    }

    private static void tileFarFromItsSlopeLeavesTheSlopeAsRead() {
        List<RoofFit.Tile> roof = new ArrayList<>();
        RoofFit.Tile first = tile(roof, 0, 0, 1, RoofFit.ALONG_X, true, 1f, 1.33f);
        // Joined at the edge it shares with the first, and then far steeper than any roof.
        RoofFit.Tile second = tile(roof, 1, 0, 1, RoofFit.ALONG_X, true, 1.35f, 3f);
        RoofFit.fit(roof);
        check(!first.fitted && !second.fitted && first.fitLow == 1f && second.fitHigh == 3f,
                "tiles that do not lie near one plane are not forced onto one");
    }

    private static void shallowRoofKeepsItsOwnPitch() {
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (int column = 0; column < 4; column++) {
            tile(roof, column, 0, 1, RoofFit.ALONG_X, true, 1.1f + column * 0.15f, 1.25f + column * 0.15f);
        }
        RoofFit.fit(roof);
        for (RoofFit.Tile tile : roof) {
            check(tile.fitted && near(tile.fitLow, 1.1f + tile.x * 0.15f) && near(tile.fitHigh - tile.fitLow, 0.15f),
                    "a roof shallower than the usual pitch is left at its own");
        }
    }

    private static void unclearTileTakesThePlaneOfItsRow() {
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                tile(roof, column, row, 1, RoofFit.ALONG_Y, true, 2f - row / 3f, 2f - (row + 1) / 3f);
            }
        }
        // At the end of the middle row, a tile whose sprite reads as level with the row above.
        RoofFit.Tile unclear = tile(roof, 3, 1, 1, RoofFit.ALONG_Y, false, 2.4f, 2.1f);
        RoofFit.fit(roof);
        check(unclear.fitted && unclear.fitAxis == RoofFit.ALONG_Y && near(unclear.fitLow, 5f / 3f) && near(unclear.fitHigh, 4f / 3f),
                "a tile whose sprite does not say how it lies takes the plane of the tile beside it in its row");
    }

    private static void unclearTileWhereTwoSlopesMeetIsLeftAlone() {
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            tile(roof, i, 2, 1, RoofFit.ALONG_Y, true, 1.33f, 1f);
            tile(roof, 2, i, 1, RoofFit.ALONG_X, true, 1.33f, 1f);
        }
        RoofFit.Tile hip = tile(roof, 2, 2, 1, RoofFit.ALONG_Y, false, 1.3f, 1f);
        RoofFit.fit(roof);
        check(!hip.fitted && hip.fitLow == 1.3f, "a tile with a slope each way beside it is a hip, and is not laid on either");
    }

    /** A frame at tile scale 2 whose texture holds only the rows from top for height. */
    private static SpriteGeometry.Frame trimmed(float top, float height) {
        SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
        frame.scale = 2f;
        frame.originU = 64f;
        frame.originV = 192f;
        frame.left = 0f;
        frame.top = top;
        frame.width = 128f;
        frame.height = height;
        frame.uStart = 0f;
        frame.vStart = 0f;
        frame.uEnd = 1f;
        frame.vEnd = 1f;
        return frame;
    }

    /** The level line of the sprite a face on the north edge of its square shows at the height up, in tiles, at one end of the square. */
    private static float rowShownAt(SpriteGeometry.Frame frame, boolean atEnd, float up) {
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        check(new SpriteGeometry().addFace(mesh, frame, 0, 0, 0, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f) > 0, "the sprite covers part of the wall");
        // Along the face's upright edge the row shown runs evenly with the height, so two of its vertices give every other.
        float[] f = mesh.vertices;
        float lowUp = Float.NaN, lowRow = 0f, highUp = Float.NaN, highRow = 0f;
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            if (!near(f[at], atEnd ? 1f : 0f)) {
                continue;
            }
            // A level line runs across the sprite on the slant, half a row down for every pixel across. With that
            // taken out, every point of a level line has the one row.
            float row = frame.top + f[at + 4] * frame.height - 0.5f * (frame.left + f[at + 3] * frame.width);
            if (Float.isNaN(lowUp) || f[at + 2] < lowUp) {
                lowUp = f[at + 2];
                lowRow = row;
            }
            if (Float.isNaN(highUp) || f[at + 2] > highUp) {
                highUp = f[at + 2];
                highRow = row;
            }
        }
        check(highUp - lowUp > 0.1f, "the face has an upright edge at that end");
        return lowRow + (highRow - lowRow) * (up - lowUp) / (highUp - lowUp);
    }

    private static void pipeAlongAWallMeetsAcrossTwoSprites() {
        // Two squares of a wall with a pipe along it. One square's sprite is a short strip
        // round the pipe, the next one's runs on down to a meter.
        SpriteGeometry.Frame strip = trimmed(20f, 60f), deep = trimmed(20f, 130f);
        // The pipe leaves the first square at its far end and enters the next at its near end,
        // at one height: 30 pixels below the top of a face 192 high.
        float up = ViewCamera.LEVEL_HEIGHT * (1f - 30f / 192f);
        float leaves = rowShownAt(strip, true, up), enters = rowShownAt(deep, false, up);
        // To within the half pixel a texel is kept inside its sprite's edge by: a pipe is several pixels thick.
        check(Math.abs(leaves - enters) < 0.5f, "both sprites show the same level line at the height they meet at, so the pipe does not step: "
                + leaves + " and " + enters);
    }

    private static void wallTopTakesItsTexelsFromBelowTheSeal() {
        SpriteGeometry.Frame frame = trimmed(0f, 256f);
        SpriteGeometry geometry = new SpriteGeometry();
        geometry.sealTop = 4f;
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        geometry.addFace(mesh, frame, 0, 0, 0, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f);
        boolean top = false, foot = false;
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            float[] f = mesh.vertices;
            if (near(f[at + 2], ViewCamera.LEVEL_HEIGHT) && near(f[at], 0f)) {
                // The wall's top at its start is drawn on row 0 of this frame: 3 pixels of inset and 4 of seal below it.
                check(Math.abs(f[at + 4] * 256f - 7f - 1.5f) < 0.01f, "the top edge takes its texels from below what is not painted, at row " + f[at + 4] * 256f);
                top = true;
            }
            if (near(f[at + 2], 0f) && near(f[at], 0f)) {
                check(Math.abs(f[at + 4] * 256f - (192f - 3f + 1.5f)) < 0.01f, "and the foot is moved by the usual inset alone");
                foot = true;
            }
        }
        check(top, "the wall reaches its level's top in a straight edge");
        check(foot, "and stands on the floor");
    }
}
