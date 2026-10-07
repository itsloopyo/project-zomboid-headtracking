package com.cameraunlock.zomboid;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

/**
 * The mod's own right arm as tools/models/arms.py packs it: a mesh hung on bones, the ways
 * its hand holds things, and the hand's own lines. Lengths are metres, in the axes the arm
 * was made in. The left arm is this one mirrored.
 */
final class ArmModel {
    private static final int MAGIC = 0x41524D53, VERSION = 2;
    /** A vertex in the file: place, normal, tangent and its handedness, texture place, the female arm's place less the male's, four bones, their four weights, how far along the arm. */
    static final int VERTEX_BYTES = (3 + 3 + 4 + 2 + 3) * Float.BYTES + 4 + 4 + Float.BYTES;
    static final int NORMAL_AT = 12, TANGENT_AT = 24, UV_AT = 40, FEMALE_AT = 48, BONES_AT = 60, WEIGHTS_AT = 64, ALONG_AT = 68;

    final String[] bones;
    final int[] parents;
    /** For each bone sixteen floats, by columns: from the bone's own axes to the model's as it rests, and back. */
    final float[] rests, unrests;
    /** For each bone sixteen floats: from its parent's axes to its own as it rests. */
    final float[] locals;
    final int vertexCount, indexCount;
    /** The file's vertices and its indices, in the order of bytes the machine keeps numbers in. */
    final ByteBuffer vertices, indices;
    /** The parts of the mesh, each a run of the indices: the bare arm, what is worn over it down to the wrist, and what is worn over the hand. */
    static final int ARM = 0, SLEEVE = 1, GLOVE = 2;
    private static final String[] PARTS = {"arm", "sleeve", "glove"};
    final int[] partFirst = new int[PARTS.length], partCount = new int[PARTS.length];
    final String[] poses;
    /** For each pose and each bone, the quaternion (w, x, y, z) that turns it from rest; for each pose, the middle of what it holds. */
    final float[][] turns, holds;
    /** The way the hand at rest runs to its finger tips, the way across it to the thumb's side, and the way out of its palm. */
    final float[] along = new float[3], across = new float[3], out = new float[3];
    final int upper, upperTwist, fore, foreTwist, wrist;
    /** The shoulder, the elbow and the wrist as the arm rests. */
    final float[] shoulder = new float[3], elbow = new float[3], hand = new float[3];
    final float upperLength, foreLength;

    ArmModel(InputStream packed) throws IOException {
        ByteArrayOutputStream whole = new ByteArrayOutputStream();
        try (GZIPInputStream in = new GZIPInputStream(packed)) {
            in.transferTo(whole);
        }
        ByteBuffer file = ByteBuffer.wrap(whole.toByteArray()).order(ByteOrder.LITTLE_ENDIAN);
        if (file.getInt() != MAGIC || file.getInt() != VERSION) {
            throw new IOException("the arms file is not version " + VERSION + " of what tools/models/arms.py writes");
        }
        int boneCount = file.getInt();
        vertexCount = file.getInt();
        indexCount = file.getInt();
        int poseCount = file.getInt();
        int partCount = file.getInt();
        if (partCount != PARTS.length) {
            throw new IOException("the arms file has " + partCount + " parts and not " + PARTS.length);
        }
        bones = new String[boneCount];
        parents = new int[boneCount];
        rests = new float[boneCount * 16];
        unrests = new float[boneCount * 16];
        locals = new float[boneCount * 16];
        for (int bone = 0; bone < boneCount; bone++) {
            bones[bone] = name(file);
            parents[bone] = file.getInt();
            if (parents[bone] >= bone) {
                throw new IOException("the arms file has the bone " + bones[bone] + " before its parent");
            }
            for (int i = 0; i < 16; i++) {
                rests[bone * 16 + i] = file.getFloat();
            }
            ArmPose.rigidInverse(rests, bone * 16, unrests, bone * 16);
            if (parents[bone] < 0) {
                System.arraycopy(rests, bone * 16, locals, bone * 16, 16);
            } else {
                ArmPose.multiply(unrests, parents[bone] * 16, rests, bone * 16, locals, bone * 16);
            }
        }
        vertices = ByteBuffer.allocateDirect(vertexCount * VERTEX_BYTES).order(ByteOrder.nativeOrder());
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            for (int i = 0; i < 15; i++) {
                vertices.putFloat(file.getFloat());
            }
            for (int i = 0; i < 8; i++) {
                vertices.put(file.get());
            }
            vertices.putFloat(file.getFloat());
        }
        vertices.flip();
        indices = ByteBuffer.allocateDirect(indexCount * Integer.BYTES).order(ByteOrder.nativeOrder());
        for (int index = 0; index < indexCount; index++) {
            indices.putInt(file.getInt());
        }
        indices.flip();
        for (int part = 0; part < PARTS.length; part++) {
            String said = name(file);
            if (!said.equals(PARTS[part])) {
                throw new IOException("part " + part + " of the arms file is called " + said + " and not " + PARTS[part]);
            }
            partFirst[part] = file.getInt();
            this.partCount[part] = file.getInt();
        }
        poses = new String[poseCount];
        turns = new float[poseCount][boneCount * 4];
        holds = new float[poseCount][3];
        for (int pose = 0; pose < poseCount; pose++) {
            poses[pose] = name(file);
            for (int i = 0; i < boneCount * 4; i++) {
                turns[pose][i] = file.getFloat();
            }
            for (int axis = 0; axis < 3; axis++) {
                holds[pose][axis] = file.getFloat();
            }
        }
        for (float[] line : new float[][] {along, across, out}) {
            for (int axis = 0; axis < 3; axis++) {
                line[axis] = file.getFloat();
            }
        }
        upper = bone("upperarm01");
        upperTwist = bone("upperarm02");
        fore = bone("lowerarm01");
        foreTwist = bone("lowerarm02");
        wrist = bone("wrist");
        head(upper, shoulder);
        head(fore, elbow);
        head(wrist, hand);
        upperLength = ArmPose.apart(shoulder, elbow);
        foreLength = ArmPose.apart(elbow, hand);
    }

    private static String name(ByteBuffer file) {
        byte[] said = new byte[file.get() & 0xFF];
        file.get(said);
        return new String(said, StandardCharsets.US_ASCII);
    }

    /** The number of a bone, which the file has to have. */
    int bone(String name) throws IOException {
        for (int bone = 0; bone < bones.length; bone++) {
            if (bones[bone].equals(name)) {
                return bone;
            }
        }
        throw new IOException("the arms file has no bone called " + name);
    }

    /** The number of a way of holding, which the file has to have. */
    int pose(String name) {
        for (int pose = 0; pose < poses.length; pose++) {
            if (poses[pose].equals(name)) {
                return pose;
            }
        }
        throw new IllegalStateException("the arms file has no pose called " + name);
    }

    private void head(int bone, float[] out) {
        out[0] = rests[bone * 16 + 12];
        out[1] = rests[bone * 16 + 13];
        out[2] = rests[bone * 16 + 14];
    }
}
