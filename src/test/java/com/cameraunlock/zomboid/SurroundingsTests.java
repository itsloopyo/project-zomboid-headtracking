package com.cameraunlock.zomboid;

/** The picture of the surroundings reflections take what is off the screen from: its faces and when each is drawn (SurroundingsSchedule). */
final class SurroundingsTests {
    private SurroundingsTests() {}

    static void run() {
        Checks.run("eachFaceIsDrawnTheWayTheCubeMapIsReadAndOfTheEyesHand", SurroundingsTests::eachFaceIsDrawnTheWayTheCubeMapIsReadAndOfTheEyesHand);
        Checks.run("aPictureIsShownOnceAllSixFacesAreIn", SurroundingsTests::aPictureIsShownOnceAllSixFacesAreIn);
        Checks.run("anEyeThatStaysPutHasAFaceDrawnEverySettledEveryFrames", SurroundingsTests::anEyeThatStaysPutHasAFaceDrawnEverySettledEveryFrames);
        Checks.run("anEyeThatMovesPastRetakeHasThePictureTakenAgainAFaceAFrame", SurroundingsTests::anEyeThatMovesPastRetakeHasThePictureTakenAgainAFaceAFrame);
        Checks.run("aPictureBegunIsDrawnFromWhereItWasBegunToTheLastFace", SurroundingsTests::aPictureBegunIsDrawnFromWhereItWasBegunToTheLastFace);
        Checks.run("theShownPictureFadesOutBetweenRetakeAndGone", SurroundingsTests::theShownPictureFadesOutBetweenRetakeAndGone);
        Checks.run("forgottenTheScheduleStartsAgainFromNothing", SurroundingsTests::forgottenTheScheduleStartsAgainFromNothing);
    }

    private static void check(boolean held, String what) {
        Checks.check(held, what);
    }

    /**
     * Which face of a cube map a way (x, y, z) in the map's own axes reads, and where on it,
     * s and t from 0 to 1, as the OpenGL specification has it (table 8.19 of 4.6).
     */
    private static float[] read(float x, float y, float z) {
        float ax = Math.abs(x), ay = Math.abs(y), az = Math.abs(z);
        int face;
        float sc, tc, ma;
        if (ax >= ay && ax >= az) {
            face = x > 0f ? 0 : 1;
            sc = x > 0f ? -z : z;
            tc = -y;
            ma = ax;
        } else if (ay >= az) {
            face = y > 0f ? 2 : 3;
            sc = x;
            tc = y > 0f ? z : -z;
            ma = ay;
        } else {
            face = z > 0f ? 4 : 5;
            sc = z > 0f ? x : -x;
            tc = -y;
            ma = az;
        }
        return new float[] {face, 0.5f * (sc / ma + 1f), 0.5f * (tc / ma + 1f)};
    }

    private static void eachFaceIsDrawnTheWayTheCubeMapIsReadAndOfTheEyesHand() {
        ViewCamera.Pose eye = new ViewCamera.Pose();
        ViewCamera.clean(eye, 0.0, 0.0, 0.0, 0.7f, 0.3f);
        float eyeHand = Math.signum(dot(cross(eye.rightX, eye.rightY, eye.rightUp, eye.upX, eye.upY, eye.upUp), eye.forwardX, eye.forwardY, eye.forwardUp));
        for (int face = 0; face < SurroundingsSchedule.FACES; face++) {
            float[] b = SurroundingsSchedule.BASES[face];
            float[] across = {b[0], b[1], b[2]}, up = {b[3], b[4], b[5]}, ahead = {b[6], b[7], b[8]};
            check(dot(across, across) == 1f && dot(up, up) == 1f && dot(ahead, ahead) == 1f && dot(across, up) == 0f && dot(across, ahead) == 0f
                    && dot(up, ahead) == 0f, "face " + face + "'s three ways are of one length and square to each other");
            float hand = Math.signum(dot(cross(across[0], across[1], across[2], up[0], up[1], up[2]), ahead[0], ahead[1], ahead[2]));
            check(hand == eyeHand, "face " + face + " is drawn with axes of the eye's own hand, as everything the world draws expects: " + hand + " against " + eyeHand);
            for (float a = -0.9f; a <= 0.91f; a += 0.3f) {
                for (float c = -0.9f; c <= 0.91f; c += 0.3f) {
                    // The pixel of the face's picture a of the way across and c of the way up, seen
                    // from where it was drawn, through a view a right angle wide each way.
                    float x = ahead[0] + a * across[0] + c * up[0], y = ahead[1] + a * across[1] + c * up[1], u = ahead[2] + a * across[2] + c * up[2];
                    float[] at = read(x, y, -u);
                    check((int) at[0] == face && Math.abs(at[1] - 0.5f * (a + 1f)) < 1e-5f && Math.abs(at[2] - 0.5f * (c + 1f)) < 1e-5f,
                            "the way face " + face + " drew at (" + a + ", " + c + ") is read from that face there: face " + (int) at[0] + " at ("
                                    + at[1] + ", " + at[2] + ")");
                }
            }
        }
    }

    private static void aPictureIsShownOnceAllSixFacesAreIn() {
        SurroundingsSchedule schedule = new SurroundingsSchedule();
        check(!schedule.shown() && schedule.weight(100.0, 200.0, 1.0) == 0f, "nothing is shown before a picture is taken");
        for (int frame = 0; frame < SurroundingsSchedule.FACES; frame++) {
            check(!schedule.shown(), "no picture is shown before its sixth face is in: frame " + frame);
            check(schedule.next(100.0, 200.0, 1.0, frame), "with nothing shown, a face is drawn every frame: frame " + frame);
            check(schedule.face == frame && schedule.into == 1, "the faces are drawn in turn into the picture not shown: face " + schedule.face + " into " + schedule.into);
            check(schedule.atX == 100.0 && schedule.atY == 200.0 && schedule.atUp == 1.0, "from where the eye is");
        }
        check(schedule.shown() && schedule.showing() == 1, "once the sixth is in, that picture is shown");
        check(schedule.shownX() == 100.0 && schedule.shownY() == 200.0 && schedule.shownUp() == 1.0, "as taken from where it was begun");
        check(schedule.weight(100.0, 200.0, 1.0) == 1f, "and the reflections take all of it there");
    }

    /** A schedule whose first picture is in, taken at (100, 200, 1) by frame 5. */
    private static SurroundingsSchedule taken() {
        SurroundingsSchedule schedule = new SurroundingsSchedule();
        for (int frame = 0; frame < SurroundingsSchedule.FACES; frame++) {
            schedule.next(100.0, 200.0, 1.0, frame);
        }
        return schedule;
    }

    private static void anEyeThatStaysPutHasAFaceDrawnEverySettledEveryFrames() {
        SurroundingsSchedule schedule = taken();
        int drawn = 0, lastAt = SurroundingsSchedule.FACES - 1;
        boolean apart = true;
        for (int frame = SurroundingsSchedule.FACES; frame < SurroundingsSchedule.FACES + SurroundingsSchedule.FACES * SurroundingsSchedule.SETTLED_EVERY; frame++) {
            // Within RETAKE, moving a little.
            if (schedule.next(100.0 + 0.01 * frame, 200.0, 1.0, frame)) {
                apart &= frame - lastAt == SurroundingsSchedule.SETTLED_EVERY;
                lastAt = frame;
                drawn++;
            }
        }
        check(drawn == SurroundingsSchedule.FACES && apart, "an eye within RETAKE of where the picture was taken has a face drawn every SETTLED_EVERY frames: "
                + drawn + " in " + SurroundingsSchedule.FACES * SurroundingsSchedule.SETTLED_EVERY + " frames, evenly apart " + apart);
        check(schedule.showing() == 0 && schedule.shownX() > 100.0, "the picture taken again so is shown in place of the first, from where it was begun");
    }

    private static void anEyeThatMovesPastRetakeHasThePictureTakenAgainAFaceAFrame() {
        SurroundingsSchedule schedule = taken();
        double x = 100.0 + SurroundingsSchedule.RETAKE + 0.5;
        int frame = SurroundingsSchedule.FACES;
        for (int face = 0; face < SurroundingsSchedule.FACES; face++, frame++) {
            check(schedule.next(x, 200.0, 1.0, frame), "an eye further than RETAKE from where the picture was taken has a face drawn every frame: face " + face);
            check(schedule.face == face && schedule.into == 0 && schedule.atX == x, "into the other picture, from where the eye is now");
            check(face == SurroundingsSchedule.FACES - 1 || schedule.showing() == 1, "while the first is still shown");
        }
        check(schedule.showing() == 0 && schedule.shownX() == x, "and with all six in, the new one is shown");
        check(!schedule.next(x, 200.0, 1.0, frame), "after which, with the eye where it was taken, the next face waits SETTLED_EVERY frames");
    }

    private static void aPictureBegunIsDrawnFromWhereItWasBegunToTheLastFace() {
        SurroundingsSchedule schedule = taken();
        double begun = 100.0 + SurroundingsSchedule.RETAKE + 0.5;
        int frame = SurroundingsSchedule.FACES;
        schedule.next(begun, 200.0, 1.0, frame++);
        boolean same = true;
        for (int face = 1; face < SurroundingsSchedule.FACES; face++, frame++) {
            schedule.next(begun + 0.4 * face, 200.0, 1.0, frame);
            same &= schedule.atX == begun;
        }
        check(same, "every face of a picture is drawn from one place, however the eye moves while it is drawn");
        check(schedule.shownX() == begun, "and the picture is shown as taken from there");
    }

    private static void theShownPictureFadesOutBetweenRetakeAndGone() {
        SurroundingsSchedule schedule = taken();
        check(schedule.weight(100.0 + SurroundingsSchedule.RETAKE, 200.0, 1.0) == 1f, "within RETAKE the reflections take all of the picture");
        check(schedule.weight(100.0, 200.0, 1.0 + SurroundingsSchedule.GONE) == 0f && schedule.weight(100.0 + 50.0, 200.0, 1.0) == 0f,
                "from GONE off, up or across, none of it");
        float last = 1f;
        boolean falling = true;
        for (double d = SurroundingsSchedule.RETAKE; d <= SurroundingsSchedule.GONE; d += 0.25) {
            float w = schedule.weight(100.0, 200.0 + d, 1.0);
            falling &= w <= last && w >= 0f && w <= 1f;
            last = w;
        }
        check(falling, "between them, less the further off");
    }

    private static void forgottenTheScheduleStartsAgainFromNothing() {
        SurroundingsSchedule schedule = taken();
        schedule.forget();
        check(!schedule.shown() && schedule.weight(100.0, 200.0, 1.0) == 0f, "with the pictures given back nothing is shown");
        check(schedule.next(100.0, 200.0, 1.0, SurroundingsSchedule.FACES) && schedule.face == 0 && schedule.into == 1,
                "and the next frame begins a picture from its first face, into the first of the two that is not shown");
    }

    private static float[] cross(float ax, float ay, float az, float bx, float by, float bz) {
        return new float[] {ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx};
    }

    private static float dot(float[] a, float x, float y, float z) {
        return a[0] * x + a[1] * y + a[2] * z;
    }

    private static float dot(float[] a, float[] b) {
        return dot(a, b[0], b[1], b[2]);
    }
}
