package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Locale;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * The light pages on the graphics card: the record of each light site (LightSites) of every
 * build ChunkBuffers holds, in one RGBA32F texture WIDE texels across, TEXELS to a record and
 * A_ROW records to a row. A build's page is its table's records in the table's order, at the
 * place it was given when the build was sent: a vertex carries its record's place in its page,
 * and the page's place is added to it then (ChunkBuffers.send). The world's vertex program reads
 * a vertex's record where that says (PipelineShaders.WORLD_VERTEX).
 *
 * The builds of every view are in it together, as their vertices are in one buffer, each build's
 * page for as long as its vertices are held. A page never moves, since its place is in its
 * vertices: the texture is made longer by doubling its rows when no gap holds a page, and the
 * longer one is filled from a copy of every row kept here. Render thread.
 */
final class LightPages {
    static final int WIDE = 1024;
    /** The texels of a record: its floats, four to a texel. */
    static final int TEXELS = LightSites.RECORD / 4;
    static final int A_ROW = WIDE / TEXELS;
    /**
     * The texture unit the world's programs read the pages from, in their vertex stage: the frame's
     * own textures take the 32 below it (Pipeline.UNITS).
     */
    static final int UNIT = 32;
    private static final int FIRST_ROWS = 64;
    /** The most rows one call sends, which bounds the memory a call is sent from. */
    private static final int ROWS_A_CALL = 64;
    private static final int GL_TEXTURE_2D = 0x0DE1, GL_RGBA = 0x1908, GL_FLOAT = 0x1406, GL_RGBA32F = 0x8814, GL_NEAREST = 0x2600,
            GL_TEXTURE_MIN_FILTER = 0x2801, GL_TEXTURE_MAG_FILTER = 0x2800, GL_TEXTURE_WRAP_S = 0x2802, GL_TEXTURE_WRAP_T = 0x2803,
            GL_CLAMP_TO_EDGE = 0x812F, GL_TEXTURE_BASE_LEVEL = 0x813C, GL_TEXTURE_MAX_LEVEL = 0x813D, GL_TEXTURE0 = 0x84C0,
            GL_MAX_TEXTURE_SIZE = 0x0D33, GL_OUT_OF_MEMORY = 0x0505, GL_UNPACK_ALIGNMENT = 0x0CF5, GL_UNPACK_ROW_LENGTH = 0x0CF2,
            GL_UNPACK_SKIP_ROWS = 0x0CF3, GL_UNPACK_SKIP_PIXELS = 0x0CF4;
    private static final BufferSpace space = new BufferSpace();
    private static int texture, rows, mostRows;
    /** Every row of the texture as the pages sent to it left it, RECORD floats a record. */
    private static float[] copy = new float[0];
    private static final FloatBuffer sending = ByteBuffer.allocateDirect(ROWS_A_CALL * WIDE * 4 * Float.BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
    /** Records in pages, for the logic thread's log. */
    private static volatile int usedRecords;

    private LightPages() {}

    /**
     * Puts the first records of a page, RECORD floats each, in a gap of the texture and the copy,
     * and says where: the place of its first record. The texture is bound to UNIT after.
     */
    static int send(float[] page, int records) {
        if (records <= 0) {
            throw new IllegalArgumentException("a page of " + records + " records");
        }
        int at = space.take(records);
        while (at < 0) {
            grow();
            at = space.take(records);
        }
        System.arraycopy(page, 0, copy, at * LightSites.RECORD, records * LightSites.RECORD);
        GL13.glActiveTexture(GL_TEXTURE0 + UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, texture);
        unpacked();
        int end = at + records;
        int firstRow = at / A_ROW, lastRow = (end - 1) / A_ROW;
        if (firstRow == lastRow) {
            sendPart(firstRow, at - firstRow * A_ROW, records);
        } else {
            int whole = firstRow + 1;
            if (at > firstRow * A_ROW) {
                sendPart(firstRow, at - firstRow * A_ROW, whole * A_ROW - at);
            } else {
                whole = firstRow;
            }
            int wholeEnd = end == (lastRow + 1) * A_ROW ? lastRow + 1 : lastRow;
            sendRows(whole, wholeEnd);
            if (wholeEnd == lastRow) {
                sendPart(lastRow, 0, end - lastRow * A_ROW);
            }
        }
        GL13.glActiveTexture(GL_TEXTURE0);
        usedRecords += records;
        return at;
    }

    /** Gives back the place of a page that send gave, with how many records it holds. */
    static void give(int at, int records) {
        space.give(at, records);
        usedRecords -= records;
    }

    /**
     * Binds the texture to UNIT for a program of the world's to read, and tells the program how
     * many rows it has. The frame has set aside any sampler the game left on the unit.
     */
    static void bind(Program to) {
        GL13.glActiveTexture(GL_TEXTURE0 + UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, texture);
        GL13.glActiveTexture(GL_TEXTURE0);
        to.set("uLightPages", UNIT);
        to.set("uPageRows", (float) rows);
    }

    /** Gives the texture back, once no build holds a page in it. */
    static void letGo() {
        if (usedRecords != 0) {
            throw new IllegalStateException("the light pages are let go with " + usedRecords + " records still in them");
        }
        if (texture != 0) {
            GL11.glDeleteTextures(texture);
        }
        texture = 0;
        rows = 0;
        copy = new float[0];
        space.reset(0);
    }

    /** Dev, for LightCheck: every row as the pages sent to it left it, RECORD floats a record from the first. */
    static float[] records() {
        return copy;
    }

    static String describe() {
        return String.format(Locale.ROOT, "light pages hold %d records of %d", usedRecords, rows * A_ROW);
    }

    /** Doubles the rows of the texture, the first time makes it, and fills it from the copy. */
    private static void grow() {
        int more = rows == 0 ? FIRST_ROWS : rows * 2;
        if (mostRows == 0) {
            mostRows = GL11.glGetInteger(GL_MAX_TEXTURE_SIZE);
        }
        if (more > mostRows) {
            throw new IllegalStateException("the light pages need " + more + " rows of " + WIDE + " texels, and the graphics card's textures hold " + mostRows);
        }
        Pipeline.leftBefore("the light pages");
        int made = GL11.glGenTextures();
        GL13.glActiveTexture(GL_TEXTURE0 + UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, made);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_BASE_LEVEL, 0);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, 0);
        GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, WIDE, more, 0, GL_RGBA, GL_FLOAT, (FloatBuffer) null);
        int error = GL11.glGetError();
        if (error != 0) {
            GL11.glDeleteTextures(made);
            GL11.glBindTexture(GL_TEXTURE_2D, texture);
            GL13.glActiveTexture(GL_TEXTURE0);
            String what = String.format(Locale.ROOT, "the light pages of %d rows (OpenGL error 0x%x)", more, error);
            if (error != GL_OUT_OF_MEMORY) {
                throw new IllegalStateException(what);
            }
            Pipeline.noted(error);
            throw new VideoMemory.Refused(what);
        }
        copy = Arrays.copyOf(copy, more * A_ROW * LightSites.RECORD);
        if (rows > 0) {
            unpacked();
            sendRows(0, rows);
            GL11.glDeleteTextures(texture);
            // The rows added are free, after every page where it was: given back as one block, they join the gap the old rows ended in.
            space.give(rows * A_ROW, (more - rows) * A_ROW);
        } else {
            space.reset(more * A_ROW);
        }
        GL13.glActiveTexture(GL_TEXTURE0);
        texture = made;
        Log.info("first person keeps the light pages in " + more + " rows on the graphics card, " + ((long) more * A_ROW * LightSites.RECORD * Float.BYTES >> 20) + " MB");
        rows = more;
    }

    /** The pixel store as the copy is laid out: rows of whole texels one after another. */
    private static void unpacked() {
        GL11.glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
        GL11.glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
    }

    /** Sends records of one row, from the one at from in it, to the texture bound. */
    private static void sendPart(int row, int from, int records) {
        int floats = records * LightSites.RECORD;
        sending.clear();
        sending.put(copy, (row * A_ROW + from) * LightSites.RECORD, floats);
        sending.flip();
        GL11.glTexSubImage2D(GL_TEXTURE_2D, 0, from * TEXELS, row, records * TEXELS, 1, GL_RGBA, GL_FLOAT, sending);
    }

    /** Sends whole rows, from one up to but not another, to the texture bound. */
    private static void sendRows(int from, int to) {
        for (int row = from; row < to; row += ROWS_A_CALL) {
            int count = Math.min(ROWS_A_CALL, to - row);
            sending.clear();
            sending.put(copy, row * A_ROW * LightSites.RECORD, count * A_ROW * LightSites.RECORD);
            sending.flip();
            GL11.glTexSubImage2D(GL_TEXTURE_2D, 0, 0, row, WIDE, count, GL_RGBA, GL_FLOAT, sending);
        }
    }
}
