package com.cameraunlock.zomboid;

import java.util.Random;
import java.util.function.DoubleUnaryOperator;

/** Tests of what is read off a sprite's outline, and of the depth a place far across the map is drawn at. */
final class ShapeTests {
    private ShapeTests() {}

    static void run() {
        Checks.run("copingWithItsShadowUnderItIsTheSlabAndNotABoxAsDeepAsTheShadow", ShapeTests::copingWithItsShadowUnderItIsTheSlabAndNotABoxAsDeepAsTheShadow);
        Checks.run("solidPaintIsReadOffASheetOfAnotherSizeThanTheSprite", ShapeTests::solidPaintIsReadOffASheetOfAnotherSizeThanTheSprite);
        Checks.run("aCopingKeepsTheSlantOfItsSpriteAlongEveryEdgeSoItsLengthsMeet", ShapeTests::aCopingKeepsTheSlantOfItsSpriteAlongEveryEdgeSoItsLengthsMeet);
        Checks.run("aWallOverlayPaintedLowInItsFrameKeepsItsCoursesLevelAlongTheWall", ShapeTests::aWallOverlayPaintedLowInItsFrameKeepsItsCoursesLevelAlongTheWall);
        Checks.run("slabOverAShopFrontIsABeamAlongItsWall", ShapeTests::slabOverAShopFrontIsABeamAlongItsWall);
        Checks.run("sillAlongANorthWallIsABeamAtItsFloor", ShapeTests::sillAlongANorthWallIsABeamAtItsFloor);
        Checks.run("billboardSheetIsAPanelOnItsLine", ShapeTests::billboardSheetIsAPanelOnItsLine);
        Checks.run("endSheetStandsInThePlaneOfTheWholeOneBesideIt", ShapeTests::endSheetStandsInThePlaneOfTheWholeOneBesideIt);
        Checks.run("rowOfLettersIsALowPanel", ShapeTests::rowOfLettersIsALowPanel);
        Checks.run("binAndPostAreNeitherPanelNorBeam", ShapeTests::binAndPostAreNeitherPanelNorBeam);
        Checks.run("beamHasAnUndersideAndPanelIsOneUprightFace", ShapeTests::beamHasAnUndersideAndPanelIsOneUprightFace);
        Checks.run("depthFarAcrossTheMapKeepsItsOrderFromTheEye", ShapeTests::depthFarAcrossTheMapKeepsItsOrderFromTheEye);
        Checks.run("lampProudOfItsWallIsFoundWhereItIsFixed", ShapeTests::lampProudOfItsWallIsFoundWhereItIsFixed);
        Checks.run("newModelsAreInTheJar", ShapeTests::newModelsAreInTheJar);
        Checks.run("footOfAPostIsItsCornerNearestTheView", ShapeTests::footOfAPostIsItsCornerNearestTheView);
        Checks.run("cardOnAnUpperFloorStopsAtTheFloor", ShapeTests::cardOnAnUpperFloorStopsAtTheFloor);
        Checks.run("squarePostIsReadWithItsSidesAlongTheGrid", ShapeTests::squarePostIsReadWithItsSidesAlongTheGrid);
        Checks.run("columnIsReadRoundWithItsBaseAndItsAxis", ShapeTests::columnIsReadRoundWithItsBaseAndItsAxis);
        Checks.run("postWithBracesHasAnArmEachWay", ShapeTests::postWithBracesHasAnArmEachWay);
        Checks.run("spireAndCrateAreNoPosts", ShapeTests::spireAndCrateAreNoPosts);
        Checks.run("postIsASolidRoundItsAxisAndItsFarSideMirrorsItsNear", ShapeTests::postIsASolidRoundItsAxisAndItsFarSideMirrorsItsNear);
        Checks.run("strutIsABoxAlongItsLine", ShapeTests::strutIsABoxAlongItsLine);
        Checks.run("headOfAPostIsTheCardAboveItsShaft", ShapeTests::headOfAPostIsTheCardAboveItsShaft);
        Checks.run("pipeWithABendAtItsFootIsAPostFromTheBendUp", ShapeTests::pipeWithABendAtItsFootIsAPostFromTheBendUp);
        Checks.run("litterAndLowSlabsLieFlat", ShapeTests::litterAndLowSlabsLieFlat);
        Checks.run("cardOfABushBesideAFenceIsCutToItsOwnSideInAnyWind", ShapeTests::cardOfABushBesideAFenceIsCutToItsOwnSideInAnyWind);
    }

    private static void cardOfABushBesideAFenceIsCutToItsOwnSideInAnyWind() {
        // A bush painted the whole width of its frame, from its foot to two thirds of a level up.
        SpriteGeometry.Frame frame = frame();
        frame.left = 0f;
        frame.top = 96f;
        frame.width = 128f;
        frame.height = 128f;
        SpriteGeometry geometry = new SpriteGeometry();
        SpriteGeometry.Mesh open = new SpriteGeometry.Mesh(), kept = new SpriteGeometry.Mesh();
        geometry.addUpright(open, frame, 5, 7, 0, 0.5f, 0.5f, 0f, 1f, 0f, false, true);
        float openEast = -Float.MAX_VALUE, openWest = Float.MAX_VALUE;
        for (int at = 0; at < open.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            openEast = Math.max(openEast, open.vertices[at]);
            openWest = Math.min(openWest, open.vertices[at]);
        }
        check(openEast > 6.15f, "with nothing beside it the card reaches past its square: " + openEast);
        geometry.roomHighA = 0.94f;
        geometry.addUpright(kept, frame, 5, 7, 0, 0.5f, 0.5f, 0f, 1f, 0f, false, true);
        check(kept.triangles() == 2, "beside a fence on its east edge it is still one card");
        float sway = 0f, east = -Float.MAX_VALUE, west = Float.MAX_VALUE, eastU = 0f;
        for (int at = 0; at < kept.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            float nx = kept.vertices[at + 9], ny = kept.vertices[at + 10], nz = kept.vertices[at + 11];
            sway = Math.max(sway, (float) Math.sqrt(nx * nx + ny * ny + nz * nz) - 1f);
            if (kept.vertices[at] > east) {
                east = kept.vertices[at];
                eastU = kept.vertices[at + 3];
            }
            west = Math.min(west, kept.vertices[at]);
        }
        check(sway > 0.2f && near(east, 5.94f - SpriteGeometry.SWAY_REACH * sway, 1e-4f),
                "it ends short of the fence by as far as the strongest wind carries its top: " + east + " swaying " + sway);
        check(near(eastU, (east - openWest) / (openEast - openWest), 1e-4f), "and shows its sprite as far as it reaches, not squeezed into what is left: " + eastU);
        check(west == openWest, "its other end is where it was");
        kept.clear();
        // Turned the other way, its first end is the one at the fence.
        geometry.addUpright(kept, frame, 5, 7, 0, 0.5f, 0.5f, 0f, -1f, 0f, false, true);
        float turnedEast = -Float.MAX_VALUE, turnedEastU = 0f, turnedWestU = 0f, turnedWest = Float.MAX_VALUE;
        for (int at = 0; at < kept.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            if (kept.vertices[at] > turnedEast) {
                turnedEast = kept.vertices[at];
                turnedEastU = kept.vertices[at + 3];
            }
            if (kept.vertices[at] < turnedWest) {
                turnedWest = kept.vertices[at];
                turnedWestU = kept.vertices[at + 3];
            }
        }
        check(near(turnedEast, east, 1e-4f) && near(turnedEastU, (openEast - turnedEast) / (openEast - openWest), 1e-4f) && turnedWestU == 1f && turnedWest == openWest,
                "turned the other way it is cut at the same place, from its other end: " + turnedEast + " showing from " + turnedEastU);
        kept.clear();
        geometry.addUpright(kept, frame, 5, 7, 0, 0.5f, 0.5f, 0f, 0f, 1f, false, true);
        check(kept.triangles() == 2, "a card along the fence is left whole");
        for (int at = 0; at < kept.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            check(kept.vertices[at] == 5.5f, "and where it stood");
        }
        kept.clear();
        geometry.nudgeX = 0.5f;
        geometry.addUpright(kept, frame, 5, 7, 0, 0.5f, 0.5f, 0f, 0f, 1f, false, true);
        check(kept.floats == 0, "and one wholly on the far side of a fence is not there at all");
    }

    private static void pipeWithABendAtItsFootIsAPostFromTheBendUp() {
        SpriteGeometry.Frame frame = frame();
        SpriteGeometry.Frame whole = frame;
        float a = 0.3f, b = 0.3f;
        SpriteShape.Painted painted = (u, v) -> {
            for (float h = 0.16f; h <= 1f; h += 0.004f) {
                float du = (u + 0.5f - SpriteGeometry.frameU(whole, a, b)) / (0.07f * 1.4142f * 64f);
                float dv = (v + 0.5f - SpriteGeometry.frameV(whole, a, b, h)) / (0.07f * 1.4142f * 32f);
                if (du * du + dv * dv <= 1f) {
                    return true;
                }
            }
            // The bend: the pipe turned out along x at the floor.
            for (float t = 0f; t <= 1f; t += 0.02f) {
                float du = u + 0.5f - SpriteGeometry.frameU(whole, a + 0.3f * t, b), dv = v + 0.5f - SpriteGeometry.frameV(whole, a + 0.3f * t, b, 0.16f - 0.1f * t);
                if (du * du + dv * dv <= 36f) {
                    return true;
                }
            }
            return false;
        };
        trim(frame, painted);
        Posts post = Posts.read(painted, frame, false);
        check(post != null && post.kind == Posts.ROUND, "a pipe with a bend at its foot is a round post");
        check(post.footShare > 0.08f && post.footShare < 0.3f, "whose foot is the bend, left as its sprite paints it: " + post.footShare);
        check(post.heights[0] > 0.08f && post.heights[0] < 0.3f && near(post.top(), 1f, 0.06f), "and whose shaft starts above the bend: " + post.heights[0] + " to " + post.top());
        check(near(post.half, 0.07f, 0.012f) && near(post.a - post.b, a - b, 0.04f), "as thick as the pipe, on its line across the view: " + post.half + " at " + post.a + ", " + post.b);
    }

    private static void litterAndLowSlabsLieFlat() {
        check(Flat.lies("Floor", null) && Flat.lies(null, "FloorRug") && !Flat.lies(null, "WallObject") && !Flat.lies(null, null),
                "what the game draws with the floor or lets be moved as a rug lies on the ground");
        check(Flat.isSlab(0.4f, 0.1f), "a vent on a roof is a slab");
        check(!Flat.isSlab(0.4f, 0.5f) && !Flat.isSlab(0.1f, 0.05f) && !Flat.isSlab(0.25f, 0.2f), "a crate, a pebble and a stool are not");
    }

    /** The picture the game's view has of something turned about an upright axis at (a, b): radius(h) tiles thick at h levels, up to high. */
    private static SpriteShape.Painted turned(SpriteGeometry.Frame frame, float a, float b, float high, DoubleUnaryOperator radius) {
        SpriteShape.Painted painted = (u, v) -> {
            for (float h = 0f; h <= high; h += 0.004f) {
                float across = (float) radius.applyAsDouble(h) * (float) Math.sqrt(2.0) * 64f;
                float du = (u + 0.5f - SpriteGeometry.frameU(frame, a, b)) / across, dv = (v + 0.5f - SpriteGeometry.frameV(frame, a, b, h)) / (across / 2f);
                if (du * du + dv * dv <= 1f) {
                    return true;
                }
            }
            return false;
        };
        trim(frame, painted);
        return painted;
    }

    private static void squarePostIsReadWithItsSidesAlongTheGrid() {
        SpriteGeometry.Frame frame = frame();
        Posts post = Posts.read(box(frame, 0.4f, 0.3f, 0f, 0.6f, 0.5f, 1f), frame, false);
        check(post != null && post.kind == Posts.SQUARE, "a box much taller than it is wide is a square post");
        check(near(post.a, 0.5f, 0.03f) && near(post.b, 0.4f, 0.03f), "its axis is the middle of the box: " + post.a + ", " + post.b);
        check(near(post.half, 0.1f, 0.012f) && near(post.top(), 1f, 0.03f), "half as thick as the box is wide, and as high: " + post.half + ", " + post.top());
        check(post.arms.length == 0 && !post.headed, "with nothing above its shaft");
        frame = frame();
        check(Posts.read(box(frame, 0.4f, 0.3f, 0f, 0.6f, 0.5f, 1f), frame, true).kind == Posts.ROUND, "what the game says is a trunk is round whatever its foot");
    }

    private static void columnIsReadRoundWithItsBaseAndItsAxis() {
        SpriteGeometry.Frame frame = frame();
        Posts post = Posts.read(turned(frame, 0.45f, 0.5f, 1f, h -> h < 0.08 ? 0.5 : 0.36), frame, false);
        check(post != null && post.kind == Posts.ROUND, "a column is a round post");
        check(near(post.a, 0.45f, 0.03f) && near(post.b, 0.5f, 0.03f), "on its axis: " + post.a + ", " + post.b);
        check(near(post.half, 0.36f, 0.015f) && near(post.halves[0], 0.5f, 0.02f), "its shaft and its base each as thick as they are: " + post.half + ", " + post.halves[0]);
        check(near(post.top(), 1f, 0.03f) && post.heights.length >= 3 && post.heights.length <= 8, "a level high, in a few stations: " + post.top() + ", " + post.heights.length);
        float baseEnds = 0f;
        for (int i = 0; i < post.heights.length; i++) {
            if (post.halves[i] > 0.45f) {
                baseEnds = post.heights[i];
            }
        }
        // The far rim of the base shows over its top, so its outline narrows to the shaft over a little more height than the base has.
        check(baseEnds >= 0.06f && baseEnds <= 0.2f, "the base is about as high as it is drawn: " + baseEnds);
    }

    private static void postWithBracesHasAnArmEachWay() {
        SpriteGeometry.Frame frame = frame();
        SpriteGeometry.Frame whole = frame;
        float a = 0.5f, b = 0.15f;
        SpriteShape.Painted shaft = box(frame, a - 0.08f, b - 0.08f, 0f, a + 0.08f, b + 0.08f, 0.8f);
        SpriteShape.Painted painted = (u, v) -> {
            if (shaft.at(u, v)) {
                return true;
            }
            for (int way = -1; way <= 1; way += 2) {
                for (float t = 0f; t <= 1f; t += 0.01f) {
                    float du = u + 0.5f - SpriteGeometry.frameU(whole, a + way * 0.6f * t, b);
                    float dv = v + 0.5f - SpriteGeometry.frameV(whole, a + way * 0.6f * t, b, 0.78f + 0.22f * t);
                    if (du * du + dv * dv <= 49f) {
                        return true;
                    }
                }
            }
            return false;
        };
        trim(frame, painted);
        Posts post = Posts.read(painted, frame, false);
        check(post != null && post.kind == Posts.SQUARE && post.arms.length == 8, "a post with a brace each side is a square post with two arms");
        check(near(post.a, a, 0.04f) && near(post.b, b, 0.04f), "on its own axis, not the middle of its sprite: " + post.a + ", " + post.b);
        check(post.armsAlongX(), "braces that rise alike along x lie along x");
        float[] to = new float[3];
        post.armTo(0, true, to);
        check(to[0] < -0.5f && to[0] > -0.75f && to[1] == 0f && near(post.armsFrom + to[2], 1f, 0.1f), "the first reaches west and up to the beam: " + to[0] + ", " + to[2]);
        post.armTo(1, true, to);
        check(to[0] > 0.5f && to[0] < 0.75f && near(post.armsFrom + to[2], 1f, 0.1f), "the second east: " + to[0] + ", " + to[2]);
    }

    private static void spireAndCrateAreNoPosts() {
        SpriteGeometry.Frame frame = frame();
        check(Posts.read(turned(frame, 0.5f, 0.5f, 1.1f, h -> 0.4 * (1.0 - h / 1.1) + 0.005), frame, false) == null, "a spire is no post: it has no one thickness");
        frame = frame();
        check(Posts.read(box(frame, 0.1f, 0.1f, 0f, 0.9f, 0.9f, 0.5f), frame, false) == null, "nor is a crate");
        frame = frame();
        check(Posts.read(box(frame, 0.45f, 0.45f, 0f, 0.55f, 0.55f, 0.2f), frame, false) == null, "nor a peg too low to be one");
    }

    private static void postIsASolidRoundItsAxisAndItsFarSideMirrorsItsNear() {
        SpriteGeometry.Frame frame = frame();
        Posts post = Posts.read(turned(frame, 0.5f, 0.5f, 1f, h -> 0.2), frame, false);
        frame.light(1f, 1f, 1f);
        SpriteGeometry geometry = new SpriteGeometry();
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        geometry.addPost(mesh, frame, 3, 4, 1, post, 0.5f, 0.5f, 0f, true, false, 0f, 0f);
        check(mesh.triangles() == 12 * 2 * (post.heights.length - 1) + 12, "a round post is a ring of sides between each two stations and a top: " + mesh.triangles());
        float lowest = Float.MAX_VALUE, highest = 0f, furthest = 0f;
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            float dx = mesh.vertices[at] - 3.5f, dy = mesh.vertices[at + 1] - 4.5f;
            furthest = Math.max(furthest, (float) Math.sqrt(dx * dx + dy * dy));
            lowest = Math.min(lowest, mesh.vertices[at + 2]);
            highest = Math.max(highest, mesh.vertices[at + 2]);
            check(mesh.vertices[at + 3] >= 0f && mesh.vertices[at + 3] <= 1f && mesh.vertices[at + 4] >= 0f && mesh.vertices[at + 4] <= 1f, "every texel is on the sprite");
        }
        check(near(furthest, 0.2f, 0.015f), "its surface is its radius from its axis: " + furthest);
        check(near(lowest, ViewCamera.LEVEL_HEIGHT, 1e-4f) && near(highest, 2f * ViewCamera.LEVEL_HEIGHT, 0.08f), "from its floor to its top: " + lowest + ", " + highest);
        float[] u = new float[1], mirrored = new float[1];
        float v = SpriteGeometry.postTexel(frame, 0.5f, 0.5f, -0.15f, -0.05f, 0.5f, 1f, u);
        float mirroredV = SpriteGeometry.postTexel(frame, 0.5f, 0.5f, 0.05f, 0.15f, 0.5f, 1f, mirrored);
        check(u[0] == mirrored[0] && v == mirroredV, "a point the view did not see shows what its mirror image across the view shows");
        float edgeV = SpriteGeometry.postTexel(frame, 0.5f, 0.5f, -0.1f, 0.1f, 0.5f, 1f, u);
        float beside = SpriteGeometry.postTexel(frame, 0.5f, 0.5f, -0.101f, 0.099f, 0.5f, 1f, mirrored);
        check(near(u[0], mirrored[0], 0.02f) && near(edgeV, beside, 0.02f), "so the two sides meet at the outline with no seam");
        mesh.clear();
        frame = frame();
        Posts square = Posts.read(box(frame, 0.4f, 0.4f, 0f, 0.6f, 0.6f, 1f), frame, false);
        frame.light(1f, 1f, 1f);
        geometry.addPost(mesh, frame, 3, 4, 0, square, square.a, square.b, 1.3f, true, true, 0.25f, 0.75f);
        check(mesh.triangles() == 4 * 2 + 4, "a square post is four sides and a top");
        highest = 0f;
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            highest = Math.max(highest, mesh.vertices[at + 2]);
            check(mesh.vertices[at + 3] == 0.25f && mesh.vertices[at + 4] == 0.75f, "timber takes the one texel it is given");
            float dx = Math.abs(mesh.vertices[at] - 3f - square.a), dy = Math.abs(mesh.vertices[at + 1] - 4f - square.b);
            check(dx < square.half + 1e-3f && dy < square.half + 1e-3f, "its sides lie along the grid");
        }
        check(near(highest, 1.3f * ViewCamera.LEVEL_HEIGHT, 1e-4f), "and it is made as high as its braces reach when that is higher than its shaft");
    }

    private static void strutIsABoxAlongItsLine() {
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        float[] from = {1f, 2f, 1f}, to = {1.6f, 2f, 1.5f};
        SpriteGeometry.addStrut(mesh, from, to, 0.05f, 0.5f, 0.5f, 1f, 1f, 1f);
        check(mesh.triangles() == 12, "a strut is four sides and two ends");
        float length = (float) Math.sqrt(0.6f * 0.6f + 0.5f * 0.5f);
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            float dx = mesh.vertices[at] - 1f, dy = mesh.vertices[at + 1] - 2f, dz = mesh.vertices[at + 2] - 1f;
            float along = (dx * 0.6f + dz * 0.5f) / length;
            float off = (float) Math.sqrt(Math.max(0f, dx * dx + dy * dy + dz * dz - along * along));
            check(along > -1e-4f && along < length + 1e-4f && near(off, 0.05f * (float) Math.sqrt(2.0), 1e-4f), "every corner is half a thickness each way off its line");
        }
    }

    private static void headOfAPostIsTheCardAboveItsShaft() {
        SpriteGeometry.Frame frame = frame();
        frame.left = 40f;
        frame.top = 0f;
        frame.width = 48f;
        frame.height = 224f;
        SpriteGeometry geometry = new SpriteGeometry();
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        geometry.skipped = 0.75f;
        geometry.addUpright(mesh, frame, 0, 0, 0, 0.5f, 0.5f, 0f, 1f, 0f, false, false);
        float lowest = Float.MAX_VALUE, lowestV = 0f;
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            lowest = Math.min(lowest, mesh.vertices[at + 2]);
            lowestV = Math.max(lowestV, mesh.vertices[at + 4]);
        }
        float foot = (224f - 224f) * ViewCamera.LEVEL_HEIGHT / 192f;
        check(near(lowest, foot + 0.75f * 224f * ViewCamera.LEVEL_HEIGHT / 192f, 1e-3f) && near(lowestV, 0.25f, 1e-4f), "a card with its shaft left out starts where the shaft ends: " + lowest + ", " + lowestV);
    }

    private static void cardOnAnUpperFloorStopsAtTheFloor() {
        // A chair whose sprite covers its whole square: its near corner is drawn 32 pixels below the square's middle.
        SpriteGeometry.Frame frame = frame();
        frame.left = 20f;
        frame.top = 120f;
        frame.width = 88f;
        frame.height = 136f;
        SpriteGeometry geometry = new SpriteGeometry();
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        geometry.addUpright(mesh, frame, 5, 7, 1, 0.5f, 0.5f, 0f, 1f, 0f, false, false);
        float floor = ViewCamera.LEVEL_HEIGHT, lowest = Float.MAX_VALUE;
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            lowest = Math.min(lowest, mesh.vertices[at + 2]);
        }
        check(lowest < floor - 0.3f, "with no floor under it a card reaches below its square as far as its sprite does: " + lowest);
        mesh.clear();
        geometry.floored = true;
        geometry.addUpright(mesh, frame, 5, 7, 1, 0.5f, 0.5f, 0f, 1f, 0f, false, false);
        lowest = Float.MAX_VALUE;
        float lowestV = 0f;
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            lowest = Math.min(lowest, mesh.vertices[at + 2]);
            lowestV = Math.max(lowestV, mesh.vertices[at + 4]);
        }
        check(near(lowest, floor, 1e-4f), "on a floor it stops at the floor, and nothing of it hangs from the ceiling below: " + lowest);
        check(near(lowestV, 1f - 32f / 136f, 1e-4f), "and shows its sprite from the row drawn at the floor up: " + lowestV);
        check(mesh.triangles() == 2, "it is still one card");
        mesh.clear();
        geometry.raised = 0.5f;
        geometry.addUpright(mesh, frame, 5, 7, 1, 0.5f, 0.5f, 0f, 1f, 0f, false, false);
        lowest = Float.MAX_VALUE;
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            lowest = Math.min(lowest, mesh.vertices[at + 2]);
        }
        check(near(lowest, floor + 0.5f * ViewCamera.LEVEL_HEIGHT - 32f * ViewCamera.LEVEL_HEIGHT / 192f, 1e-4f), "what the game draws half a level up stands half a level up, whole: " + lowest);
        check(SpriteGeometry.cutBelow(-1f, 3f, 0f) == 0.25f && SpriteGeometry.cutBelow(0.5f, 3f, 0f) == 0f && SpriteGeometry.cutBelow(-3f, -1f, 0f) >= 1f,
                "the share of a card below a height is how far up it the height is");
    }

    private static void footOfAPostIsItsCornerNearestTheView() {
        // A post a sixth of a square thick against the middle of the west edge, taller than a level.
        SpriteGeometry.Frame frame = frame();
        SpriteShape.Painted post = box(frame, 0f, 0.42f, 0f, 0.16f, 0.58f, 1.1f);
        float[] foot = new float[3];
        check(SpriteShape.foot(post, frame, foot), "a post has a foot");
        check(near(foot[0], 0.16f, 0.06f) && near(foot[1], 0.58f, 0.06f), "it is the post's south-east corner on the floor: " + foot[0] + ", " + foot[1]);
        check(foot[2] < 0.3f, "and is as narrow as the post: " + foot[2]);
        frame = frame();
        SpriteShape.Painted north = box(frame, 0.42f, 0f, 0f, 0.58f, 0.16f, 1.1f);
        check(SpriteShape.foot(north, frame, foot) && near(foot[0], 0.58f, 0.06f) && near(foot[1], 0.16f, 0.06f), "a post against the north edge has its foot there");
        frame = frame();
        frame.left = 10f;
        frame.top = 10f;
        frame.width = 20f;
        frame.height = 20f;
        check(!SpriteShape.foot((u, v) -> false, frame, foot), "a sprite with nothing painted has none");
    }

    private static void newModelsAreInTheJar() {
        for (String name : new String[] {"cone_lamp", "globe_lamp", "lantern_lamp", "lantern_head", "cobra_head", "wall_bowl", "wall_lantern",
                "bulkhead_light", "wall_sink", "serving_counter"}) {
            check(Models.get(name).triangles() > 0, name + " is a model");
        }
        Models.Model arm = Models.get("cobra_head");
        check(arm.minY < -0.8f && arm.maxY < 0.1f && Math.abs(arm.minX + arm.maxX) < 0.01f, "a road lamp's arm reaches out to its front from the post at its origin");
        check(near(Models.get("serving_counter").width(), 1f, 1e-3f), "a length of serving counter is a square wide, so a run of them meets");
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static boolean near(float a, float b, float within) {
        return Math.abs(a - b) <= within;
    }

    private static SpriteGeometry.Frame frame() {
        SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
        frame.scale = 2f;
        frame.originU = 64f;
        frame.originV = 192f;
        frame.uStart = 0f;
        frame.uEnd = 1f;
        frame.vStart = 0f;
        frame.vEnd = 1f;
        return frame;
    }

    /** Whether the pixel is inside the picture of the flat face from (a, b, h) along two edges. */
    private static boolean onFace(SpriteGeometry.Frame frame, float u, float v, float a, float b, float h, float[] one, float[] two) {
        float u0 = SpriteGeometry.frameU(frame, a, b), v0 = SpriteGeometry.frameV(frame, a, b, h);
        float u1 = SpriteGeometry.frameU(frame, a + one[0], b + one[1]) - u0, v1 = SpriteGeometry.frameV(frame, a + one[0], b + one[1], h + one[2]) - v0;
        float u2 = SpriteGeometry.frameU(frame, a + two[0], b + two[1]) - u0, v2 = SpriteGeometry.frameV(frame, a + two[0], b + two[1], h + two[2]) - v0;
        float det = u1 * v2 - u2 * v1;
        float s = ((u - u0) * v2 - u2 * (v - v0)) / det, t = (u1 * (v - v0) - (u - u0) * v1) / det;
        return s >= 0f && s <= 1f && t >= 0f && t <= 1f;
    }

    /** The picture the game's view has of a box, as a sprite: the frame is trimmed to it. */
    private static SpriteShape.Painted box(SpriteGeometry.Frame frame, float a0, float b0, float h0, float a1, float b1, float h1) {
        float[] alongA = {a1 - a0, 0f, 0f}, alongB = {0f, b1 - b0, 0f}, up = {0f, 0f, h1 - h0};
        SpriteShape.Painted painted = (u, v) -> onFace(frame, u + 0.5f, v + 0.5f, a0, b0, h1, alongA, alongB)
                || onFace(frame, u + 0.5f, v + 0.5f, a0, b1, h0, alongA, up) || onFace(frame, u + 0.5f, v + 0.5f, a1, b0, h0, alongB, up);
        trim(frame, painted);
        return painted;
    }

    /** The picture of an upright sheet from (a0, b0) to (a1, b1) on the square, h0 to h1 up. */
    private static SpriteShape.Painted sheet(SpriteGeometry.Frame frame, float a0, float b0, float a1, float b1, float h0, float h1) {
        float[] along = {a1 - a0, b1 - b0, 0f}, up = {0f, 0f, h1 - h0};
        SpriteShape.Painted painted = (u, v) -> onFace(frame, u + 0.5f, v + 0.5f, a0, b0, h0, along, up);
        trim(frame, painted);
        return painted;
    }

    private static void trim(SpriteGeometry.Frame frame, SpriteShape.Painted painted) {
        int left = Integer.MAX_VALUE, right = Integer.MIN_VALUE, top = Integer.MAX_VALUE, bottom = Integer.MIN_VALUE;
        for (int u = -64; u < 256; u++) {
            for (int v = -256; v < 320; v++) {
                if (painted.at(u, v)) {
                    left = Math.min(left, u);
                    right = Math.max(right, u);
                    top = Math.min(top, v);
                    bottom = Math.max(bottom, v);
                }
            }
        }
        frame.left = left;
        frame.top = top;
        frame.width = right - left + 1;
        frame.height = bottom - top + 1;
    }

    private static void copingWithItsShadowUnderItIsTheSlabAndNotABoxAsDeepAsTheShadow() {
        // The coping along a parapet's west edge: a slab a fiftieth of a level thick a third of a level up, and its shadow down the wall to the foot.
        SpriteGeometry.Frame frame = frame();
        SpriteShape.Painted slab = box(frame, 0.06f, 0f, 0.32f, 0.25f, 1f, 0.34f), withShadow = box(frame, 0.06f, 0f, 0.06f, 0.25f, 1f, 0.34f);
        trim(frame, withShadow);
        SpriteShape outline = SpriteShape.read(withShadow, frame);
        check(outline.kind == SpriteShape.BEAM && outline.h1 - outline.h0 > 0.2f, "the test's coping reads from its outline as a deep box, or it shows nothing: " + (outline.h1 - outline.h0));
        int left = Math.round(frame.left), top = Math.round(frame.top), across = Math.round(frame.width), down = Math.round(frame.height);
        boolean[] solid = new boolean[across * down];
        for (int row = 0; row < down; row++) {
            for (int column = 0; column < across; column++) {
                solid[row * across + column] = slab.at(left + column, top + row);
            }
        }
        SpriteShape body = SpriteShape.withoutShadow(outline, Solids.outline(solid, left, top, across, down), frame);
        check(body.kind == SpriteShape.BEAM && !body.alongX, "the coping's solid paint is a beam along its wall");
        check(body.h1 - body.h0 < 0.04f && Math.abs(body.h1 - 0.34f) < 0.02f, "it is the slab at the top of the wall, from " + body.h0 + " to " + body.h1);
        check(Math.abs(body.a0 - 0.06f) < 0.03f && Math.abs(body.a1 - 0.25f) < 0.03f && body.b0 < 0.05f && body.b1 > 0.95f, "and lies where the slab is painted");
        check(body.b0 == 0f && body.b1 == 1f, "from one edge of its square to the other exactly, so its lengths meet with no slit between them: " + body.b0 + " to " + body.b1);
        check(Math.round(frame.left) == left && Math.round(frame.height) == down, "the frame is left as it was given");
        check(SpriteShape.withoutShadow(outline, Solids.outline(new boolean[across * down], left, top, across, down), frame) == outline,
                "a sprite with no solid paint keeps the reading of its outline");
    }

    private static void solidPaintIsReadOffASheetOfAnotherSizeThanTheSprite() {
        // A sprite of 6 by 4, on a sheet drawn at twice the size: 12 by 8 at (2, 1) of a sheet 16 wide, solid in its right half and shadow in its left.
        int sheetWidth = 16;
        java.nio.ByteBuffer sheet = java.nio.ByteBuffer.allocate(sheetWidth * 10 * 4);
        for (int y = 1; y < 9; y++) {
            for (int x = 2; x < 14; x++) {
                sheet.put((y * sheetWidth + x) * 4 + 3, (byte) (x >= 8 ? Solids.SOLID : Solids.SOLID - 1));
            }
        }
        Solids.Outline outline = Solids.outline(Solids.solid(sheet, sheetWidth, 2, 1, 14, 9, 6, 4), 10, 20, 6, 4);
        check(outline.left == 13 && outline.top == 20 && outline.width == 3 && outline.height == 4,
                "the solid half of the sprite, in the sprite's own pixels: " + outline.left + ", " + outline.top + " " + outline.width + " by " + outline.height);
        check(outline.at(13, 20) && outline.at(15, 23) && !outline.at(12, 21) && !outline.at(16, 21), "paint under the threshold is no part of it");
        check(Solids.outline(Solids.solid(sheet, 0, 0, 0, 0, 0, 6, 4), 10, 20, 6, 4).width == 0, "a sprite of a sheet with no size has no solid paint");
    }

    private static void aCopingKeepsTheSlantOfItsSpriteAlongEveryEdgeSoItsLengthsMeet() {
        // The coping along a parapet's west edge, as walls_detailing_02_77 is read and trimmed: a slab 0.018 of a level thick.
        SpriteGeometry.Frame frame = frame();
        frame.left = 4f;
        frame.top = 128f;
        frame.width = 76f;
        frame.height = 92f;
        SpriteShape coping = new SpriteShape(SpriteShape.BEAM, false, true, 0.06f, 0.002f, 0.326f, 0.237f, 0.998f, 0.344f);
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        new SpriteGeometry().addBeam(mesh, frame, 0, 0, 0, coping);
        float[] f = mesh.vertices;
        int edges = 0;
        // The edges of each triangle: across two faces a line of one height is no edge.
        int triangle = 3 * SpriteGeometry.VERTEX_FLOATS;
        for (int i = 0; i < mesh.floats; i += SpriteGeometry.VERTEX_FLOATS) {
            for (int j = i + SpriteGeometry.VERTEX_FLOATS; j < (i / triangle + 1) * triangle; j += SpriteGeometry.VERTEX_FLOATS) {
                float dx = f[j] - f[i], dy = f[j + 1] - f[i + 1];
                if (Math.abs(f[j + 2] - f[i + 2]) > 1e-4f || Math.abs(dx) > 1e-4f && Math.abs(dy) > 1e-4f || Math.abs(dx) + Math.abs(dy) < 0.1f) {
                    continue;
                }
                // Along the map's y a level line runs up the sprite half a pixel for each pixel across, along x down.
                float du = (f[j + 3] - f[i + 3]) * frame.width, dv = (f[j + 4] - f[i + 4]) * frame.height;
                float slant = Math.abs(dy) > 1e-4f ? -0.5f : 0.5f;
                check(Math.abs(du) > 1f && Math.abs(dv / du - slant) < 0.02f,
                        "a level edge of the coping takes its texels along a level line of the sprite: " + dv / du + " where the sprite's slant is " + slant + ", at height " + f[i + 2]);
                edges++;
            }
        }
        check(edges >= 8, "the test found the coping's level edges: " + edges);
    }

    private static void aWallOverlayPaintedLowInItsFrameKeepsItsCoursesLevelAlongTheWall() {
        // The brick laid over a parapet, as walls_commercial_02_16 is trimmed (from column 2), and one trimmed short of the wall's end as well (column 6).
        overlayKeepsItsCourses(2f);
        overlayKeepsItsCourses(6f);
    }

    /** A wall overlay with paint only in the lowest third of a level, on a west wall, its frame from the given column. */
    private static void overlayKeepsItsCourses(float left) {
        SpriteGeometry.Frame frame = frame();
        frame.left = left;
        frame.top = 134f;
        frame.width = 66f - left;
        frame.height = 92f;
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        check(new SpriteGeometry().addFace(mesh, frame, 0, 0, 0, 0.04f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f) > 0, "the overlay covers part of the wall");
        // Along the wall a course runs up the sprite half a row for each pixel across: with that taken out, one height is one row.
        float[] f = mesh.vertices;
        float lowUp = Float.MAX_VALUE, highUp = -Float.MAX_VALUE, lowRow = 0f, highRow = 0f;
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            float row = frame.top + f[at + 4] * frame.height + 0.5f * (frame.left + f[at + 3] * frame.width);
            if (f[at + 2] < lowUp) {
                lowUp = f[at + 2];
                lowRow = row;
            }
            if (f[at + 2] > highUp) {
                highUp = f[at + 2];
                highRow = row;
            }
        }
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            float row = frame.top + f[at + 4] * frame.height + 0.5f * (frame.left + f[at + 3] * frame.width);
            float line = lowRow + (highRow - lowRow) * (f[at + 2] - lowUp) / (highUp - lowUp);
            check(Math.abs(row - line) < 0.3f, "every corner of the overlay shows the course its height has, the same at either end of the wall, frame from column " + left + ": row "
                    + row + " where " + line + ", at " + f[at + 1] + " along and " + f[at + 2] + " up");
        }
    }

    private static void slabOverAShopFrontIsABeamAlongItsWall() {
        SpriteGeometry.Frame frame = frame();
        SpriteShape shape = SpriteShape.read(box(frame, 0.67f, 0f, 0.94f, 1f, 1f, 1f), frame);
        check(shape.kind == SpriteShape.BEAM && !shape.alongX, "a slab a square long by a wall is a beam along that wall");
        check(near(shape.a0, 0.67f, 0.03f) && near(shape.a1, 1f, 0.03f), "it is as wide as painted, by the edge it was painted at: " + shape.a0 + " to " + shape.a1);
        check(near(shape.b0, 0f, 0.03f) && near(shape.b1, 1f, 0.03f), "it runs from one edge of its square to the other: " + shape.b0 + " to " + shape.b1);
        check(near(shape.h0, 0.94f, 0.015f) && near(shape.h1, 1f, 0.015f), "at the height of the ceiling it was painted at: " + shape.h0 + " to " + shape.h1);
    }

    private static void sillAlongANorthWallIsABeamAtItsFloor() {
        SpriteGeometry.Frame frame = frame();
        SpriteShape shape = SpriteShape.read(box(frame, 0f, 0.02f, 0.04f, 1f, 0.27f, 0.08f), frame);
        check(shape.kind == SpriteShape.BEAM && shape.alongX, "a sill a square long is a beam along x");
        check(near(shape.b0, 0.02f, 0.03f) && near(shape.b1, 0.27f, 0.03f) && near(shape.h1, 0.08f, 0.015f) && near(shape.h0, 0.04f, 0.015f),
                "where it was painted: b " + shape.b0 + " to " + shape.b1 + ", h " + shape.h0 + " to " + shape.h1);
    }

    private static void billboardSheetIsAPanelOnItsLine() {
        SpriteGeometry.Frame frame = frame();
        SpriteShape shape = SpriteShape.read(sheet(frame, 0f, 0.625f, 1f, 0.625f, 0.25f, 0.9f), frame);
        check(shape.kind == SpriteShape.PANEL && shape.alongX && shape.whole, "a sheet a square long is a whole panel along x");
        check(near(shape.b0, 0.625f, 0.02f) && near(shape.a0, 0f, 0.02f) && near(shape.a1, 1f, 0.02f), "on the line it was painted on: b " + shape.b0);
        check(near(shape.h0, 0.25f, 0.015f) && near(shape.h1, 0.9f, 0.015f), "from its foot to its top: " + shape.h0 + " to " + shape.h1);

        frame = frame();
        shape = SpriteShape.read(sheet(frame, 0.03f, 0f, 0.03f, 1f, 0.4f, 0.8f), frame);
        check(shape.kind == SpriteShape.PANEL && !shape.alongX && near(shape.a0, 0.03f, 0.02f) && near(shape.h1, 0.8f, 0.015f),
                "and one on a west wall is a panel along y: a " + shape.a0 + ", top " + shape.h1);
    }

    private static void endSheetStandsInThePlaneOfTheWholeOneBesideIt() {
        SpriteGeometry.Frame frame = frame();
        SpriteShape end = SpriteShape.read(sheet(frame, 0.24f, 0.625f, 1f, 0.625f, 0.25f, 0.9f), frame);
        check(end.kind == SpriteShape.PANEL && end.alongX && !end.whole, "a sheet three quarters of a square long does not say where it starts");
        SpriteShape placed = end.at(0.625f);
        check(placed.whole && near(placed.b0, 0.625f, 1e-4f) && near(placed.a0, 0.24f, 0.02f) && near(placed.a1, 1f, 0.02f),
                "in its neighbour's plane it is where it was painted: a " + placed.a0 + " to " + placed.a1);
        check(near(placed.h0, 0.25f, 0.015f) && near(placed.h1, 0.9f, 0.015f), "and as high: " + placed.h0 + " to " + placed.h1);
    }

    private static void rowOfLettersIsALowPanel() {
        SpriteGeometry.Frame frame = frame();
        SpriteShape shape = SpriteShape.read(sheet(frame, 0.03f, 0f, 0.03f, 1f, 0.02f, 0.16f), frame);
        check(shape.kind == SpriteShape.PANEL && !shape.alongX, "a strip standing on a wall's line is a panel, not a beam");
    }

    private static void binAndPostAreNeitherPanelNorBeam() {
        SpriteGeometry.Frame frame = frame();
        check(SpriteShape.read(box(frame, 0.3f, 0.3f, 0f, 0.7f, 0.7f, 0.45f), frame).kind == SpriteShape.CARD, "a bin is as wide as it is long");
        frame = frame();
        check(SpriteShape.read(box(frame, 0.45f, 0.45f, 0f, 0.55f, 0.55f, 1.1f), frame).kind == SpriteShape.CARD, "a post is neither");
        frame = frame();
        check(SpriteShape.read(box(frame, 0f, 0f, 0f, 1f, 1f, 0.3f), frame).kind == SpriteShape.CARD, "nor is a block that fills its square");
        frame = frame();
        SpriteShape.Painted disc = (u, v) -> (u - 64) * (u - 64) + (v - 100) * (v - 100) < 30 * 30;
        trim(frame, disc);
        check(SpriteShape.read(disc, frame).kind == SpriteShape.CARD, "nor is a disc");
    }

    private static void beamHasAnUndersideAndPanelIsOneUprightFace() {
        SpriteGeometry.Frame frame = frame();
        SpriteShape beam = SpriteShape.read(box(frame, 0.67f, 0f, 0.94f, 1f, 1f, 1f), frame);
        SpriteGeometry geometry = new SpriteGeometry();
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        geometry.addBeam(mesh, frame, 5, 7, 0, beam);
        float lowest = Float.MAX_VALUE, highest = -Float.MAX_VALUE;
        int under = 0;
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            float up = mesh.vertices[at + 2];
            lowest = Math.min(lowest, up);
            highest = Math.max(highest, up);
            check(mesh.vertices[at] > 5.6f && mesh.vertices[at] < 6.05f && mesh.vertices[at + 1] > 6.95f && mesh.vertices[at + 1] < 8.05f,
                    "every face of the beam is on the beam: " + mesh.vertices[at] + ", " + mesh.vertices[at + 1]);
        }
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS * 3) {
            float a = mesh.vertices[at + 2], b = mesh.vertices[at + SpriteGeometry.VERTEX_FLOATS + 2], c = mesh.vertices[at + 2 * SpriteGeometry.VERTEX_FLOATS + 2];
            if (near(a, lowest, 1e-4f) && near(b, lowest, 1e-4f) && near(c, lowest, 1e-4f)) {
                under++;
            }
        }
        check(near(highest, ViewCamera.LEVEL_HEIGHT, 0.04f) && near(lowest, 0.94f * ViewCamera.LEVEL_HEIGHT, 0.04f),
                "the beam is as deep as its sprite paints it: " + lowest + " to " + highest);
        check(under >= 2, "and has an underside, which is what is seen of an awning from the street");
        check(geometry.shownA == 0f && geometry.shownB == 0f && geometry.shownUp == 0f, "nothing added after it is moved as its hidden faces were");

        frame = frame();
        SpriteShape panel = SpriteShape.read(sheet(frame, 0f, 0.625f, 1f, 0.625f, 0.25f, 0.9f), frame);
        mesh = new SpriteGeometry.Mesh();
        geometry.addPanel(mesh, frame, 5, 7, 2, panel);
        check(mesh.triangles() >= 2, "a panel is drawn");
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            check(near(mesh.vertices[at + 1], 7.625f, 0.02f), "all of it on its line: " + mesh.vertices[at + 1]);
            check(mesh.vertices[at + 2] > 2.2f * ViewCamera.LEVEL_HEIGHT && mesh.vertices[at + 2] < 2.95f * ViewCamera.LEVEL_HEIGHT, "at its height on its level");
        }
    }

    private static void lampProudOfItsWallIsFoundWhereItIsFixed() {
        SpriteGeometry.Frame frame = frame();
        float out = 0.15f, along = 0.4f, high = 0.8f;
        for (int wall : new int[] {WallHung.NORTH, WallHung.WEST, WallHung.SOUTH, WallHung.EAST}) {
            // Where the lamp really is: out from its fixing into the square.
            float inA = wall == WallHung.WEST ? out : wall == WallHung.EAST ? -out : 0f, inB = wall == WallHung.NORTH ? out : wall == WallHung.SOUTH ? -out : 0f;
            float u = SpriteGeometry.frameU(frame, WallHung.a(wall, along) + inA, WallHung.b(wall, along) + inB);
            float v = SpriteGeometry.frameV(frame, WallHung.a(wall, along) + inA, WallHung.b(wall, along) + inB, high);
            // Where on the wall itself that pixel is: what painted reads off the sprite.
            float start = SpriteGeometry.frameU(frame, WallHung.a(wall, 0f), WallHung.b(wall, 0f));
            float read = (u - start) / (SpriteGeometry.frameU(frame, WallHung.a(wall, 1f), WallHung.b(wall, 1f)) - start);
            float readHigh = SpriteGeometry.heightDrawnAt(frame, WallHung.a(wall, read), WallHung.b(wall, read), v);
            check(near(read + WallHung.alongProud(wall, out), along, 1e-4f), "a lamp proud of wall " + wall + " is along it where it is fixed");
            check(near(readHigh + WallHung.upProud(wall, out), high, 1e-4f), "and as high as it is fixed, " + (readHigh + WallHung.upProud(wall, out)));
        }
    }

    /** What the picture's vertex program makes of a place, in the numbers the graphics card works in: its depth after the divide. */
    private static float depth(float[] matrix, float x, float y, float up) {
        float z = matrix[2] * x + matrix[6] * y + matrix[10] * up + matrix[14];
        float w = matrix[3] * x + matrix[7] * y + matrix[11] * up + matrix[15];
        return z / w;
    }

    private static float[] times(float[] projection, float[] view) {
        float[] out = new float[16];
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                float sum = 0f;
                for (int k = 0; k < 4; k++) {
                    sum += projection[k * 4 + row] * view[column * 4 + k];
                }
                out[column * 4 + row] = sum;
            }
        }
        return out;
    }

    private static void depthFarAcrossTheMapKeepsItsOrderFromTheEye() {
        // A wall's skin and the back of a picture hung a finger's width in front of it, in a
        // house ten thousand tiles from the map's corner, seen from across the room.
        ViewCamera.Pose pose = new ViewCamera.Pose();
        float[] projection = ViewCamera.projection(0.7f, 16f / 9f, 0.05f, 200f);
        Random random = new Random(4);
        int wrongOnTheMap = 0, wrongFromTheEye = 0;
        for (int i = 0; i < 2000; i++) {
            float yaw = (float) (Math.PI + (random.nextFloat() - 0.5f) * 0.6f);
            ViewCamera.clean(pose, 10777.31f, 9765.43f, 1.6f, yaw, 0f);
            float y = 9766f + random.nextFloat() * 2f, up = 1f + random.nextFloat();
            float wall = 10774.025f, picture = 10774.037f;
            float[] onTheMap = times(projection, ViewCamera.view(pose));
            if (depth(onTheMap, wall, y, up) <= depth(onTheMap, picture, y, up)) {
                wrongOnTheMap++;
            }
            float[] fromTheEye = times(projection, ViewCamera.turned(pose));
            if (depth(fromTheEye, wall - pose.eyeX, y - pose.eyeY, up - pose.eyeUp) <= depth(fromTheEye, picture - pose.eyeX, y - pose.eyeY, up - pose.eyeUp)) {
                wrongFromTheEye++;
            }
            float[] whole = ViewCamera.view(pose), turn = ViewCamera.turned(pose);
            double far = whole[2] * (double) wall + whole[6] * (double) y + whole[10] * (double) up + whole[14];
            double near = turn[2] * (double) (wall - pose.eyeX) + turn[6] * (double) (y - pose.eyeY) + turn[10] * (double) (up - pose.eyeUp);
            check(Math.abs(far - near) < 0.01, "the turn alone, of a place less the eye, is the view of the place: " + far + " and " + near);
        }
        check(wrongOnTheMap > 20, "worked out on the map, the picture is behind its wall at some places: the fault this guards against, " + wrongOnTheMap);
        check(wrongFromTheEye == 0, "worked out from the eye, the picture is in front of its wall everywhere, and was behind it at " + wrongFromTheEye);
    }
}
