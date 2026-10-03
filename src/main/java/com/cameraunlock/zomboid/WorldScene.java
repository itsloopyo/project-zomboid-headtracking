package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import zombie.characters.IsoPlayer;
import zombie.core.Core;
import zombie.core.properties.PropertyContainer;
import zombie.core.textures.ColorInfo;
import zombie.core.textures.Texture;
import zombie.core.textures.TextureDraw;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.IsoWorld;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.SpriteDetails.IsoObjectType;
import zombie.iso.objects.IsoDoor;
import zombie.iso.objects.IsoThumpable;
import zombie.iso.sprite.IsoSprite;
import zombie.util.list.PZArrayList;

/**
 * One frame of the world as the first-person view draws it.
 *
 * build() runs on the game's logic thread and reads the loaded map: every
 * square the character has seen, within reach of the eye, becomes triangles
 * textured with the sprites the game has loaded for it. render() runs later on
 * the render thread and only draws what build() wrote, so it never touches the
 * map. The scenes are reused in turn, and there are more of them than the game
 * keeps frames in flight between its two threads.
 */
final class WorldScene extends TextureDraw.GenericDrawer {
    private static final int RADIUS = 30;
    private static final int LEVELS_BELOW = 1;
    private static final int LEVELS_ABOVE = 2;
    private static final float FIELD_OF_VIEW = (float) Math.toRadians(65);
    private static final float NEAR = 0.05f;
    private static final float FAR = 150f;
    /** How far a sprite hung on a wall or laid on a floor stands off it, so the two do not fight for depth. */
    private static final float STAND_OFF = 0.01f;

    private static final int GL_TRIANGLES = 0x0004;
    private static final int GL_LEQUAL = 0x0203;
    private static final int GL_GREATER = 0x0204;
    private static final int GL_DEPTH_BUFFER_BIT = 0x0100;
    private static final int GL_COLOR_BUFFER_BIT = 0x4000;
    private static final int GL_TEXTURE_2D = 0x0DE1;
    private static final int GL_DEPTH_TEST = 0x0B71;
    private static final int GL_CULL_FACE = 0x0B44;
    private static final int GL_BLEND = 0x0BE2;
    private static final int GL_ALPHA_TEST = 0x0BC0;
    private static final int GL_SCISSOR_TEST = 0x0C11;
    private static final int GL_STENCIL_TEST = 0x0B90;
    private static final int GL_LIGHTING = 0x0B50;
    private static final int GL_FOG = 0x0B60;
    private static final int GL_MODELVIEW = 0x1700;
    private static final int GL_PROJECTION = 0x1701;
    private static final int GL_ALL_ATTRIB_BITS = 0x000FFFFF;
    private static final int GL_VIEWPORT = 0x0BA2;
    private static final int GL_DEPTH_BITS = 0x0D56;
    private static final int GL_CURRENT_PROGRAM = 0x8B8D;
    private static final int GL_TEXTURE0 = 0x84C0;

    private static final WorldScene[] scenes = {new WorldScene(), new WorldScene(), new WorldScene(), new WorldScene()};
    private static int nextScene;

    private static volatile float lastDrawMillis;
    private static volatile int depthBits = -1;
    private static boolean depthChecked;

    private final HashMap<Integer, SpriteGeometry.Mesh> meshByTexture = new HashMap<>();
    private final ArrayList<SpriteGeometry.Mesh> meshes = new ArrayList<>();
    private final ArrayList<Integer> meshTextures = new ArrayList<>();
    private final SpriteGeometry geometry = new SpriteGeometry();
    private final SpriteGeometry.Frame frame = new SpriteGeometry.Frame();

    private float eyeX, eyeY, eyeUp, yaw, pitch;
    private float rightX, rightY;
    private int squaresLoaded, squaresSeen, sprites, triangles;
    private float buildMillis;

    private WorldScene() {}

    static WorldScene next() {
        nextScene = (nextScene + 1) % scenes.length;
        return scenes[nextScene];
    }

    String describe() {
        return String.format(Locale.ROOT, "squares %d seen %d sprites %d triangles %d textures %d build %.2f ms draw %.2f ms depth bits %d",
                squaresLoaded, squaresSeen, sprites, triangles, meshes.size(), buildMillis, lastDrawMillis, depthBits);
    }

    void build(IsoPlayer player, float eyeX, float eyeY, float eyeUp, float yaw, float pitch) {
        long start = System.nanoTime();
        this.eyeX = eyeX;
        this.eyeY = eyeY;
        this.eyeUp = eyeUp;
        this.yaw = yaw;
        this.pitch = pitch;
        rightX = (float) -Math.sin(yaw);
        rightY = (float) Math.cos(yaw);
        for (int i = 0; i < meshes.size(); i++) {
            meshes.get(i).clear();
        }
        squaresLoaded = 0;
        squaresSeen = 0;
        sprites = 0;
        triangles = 0;

        int playerIndex = player.getIndex();
        IsoCell cell = IsoWorld.instance.currentCell;
        int centreX = (int) Math.floor(eyeX);
        int centreY = (int) Math.floor(eyeY);
        int level = (int) Math.floor(player.getZ());
        frame.scale = Core.tileScale;

        for (int z = level - LEVELS_BELOW; z <= level + LEVELS_ABOVE; z++) {
            for (int x = centreX - RADIUS; x <= centreX + RADIUS; x++) {
                for (int y = centreY - RADIUS; y <= centreY + RADIUS; y++) {
                    IsoGridSquare square = cell.getGridSquare(x, y, z);
                    if (square == null) {
                        continue;
                    }
                    squaresLoaded++;
                    if (!square.isSeen(playerIndex)) {
                        continue;
                    }
                    squaresSeen++;
                    addSquare(square, x, y, z, playerIndex);
                }
            }
        }
        for (int i = 0; i < meshes.size(); i++) {
            triangles += meshes.get(i).triangles();
        }
        buildMillis = (System.nanoTime() - start) / 1e6f;
    }

    private void addSquare(IsoGridSquare square, int x, int y, int z, int playerIndex) {
        ColorInfo light = square.getLightInfo(playerIndex);
        if (light == null) {
            return;
        }
        frame.red = light.r;
        frame.green = light.g;
        frame.blue = light.b;

        IsoObject floor = square.getFloor();
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoObject object = objects.get(i);
            IsoSprite sprite = object.getSprite();
            if (sprite == null) {
                continue;
            }
            Texture texture = sprite.getTextureForCurrentFrame(object.getDir());
            if (texture == null || texture.getID() <= 0 || texture.getWidth() <= 0 || texture.getHeight() <= 0) {
                continue;
            }
            frame.originU = object.offsetX;
            frame.originV = object.offsetY;
            frame.left = texture.getOffsetX();
            frame.top = texture.getOffsetY();
            frame.width = texture.getWidth();
            frame.height = texture.getHeight();
            frame.uStart = texture.getXStart();
            frame.uEnd = texture.getXEnd();
            frame.vStart = texture.getYStart();
            frame.vEnd = texture.getYEnd();
            sprites++;
            addSprite(meshFor(texture.getID()), object, sprite, object == floor, x, y, z, i);
        }
    }

    private void addSprite(SpriteGeometry.Mesh mesh, IsoObject object, IsoSprite sprite, boolean isFloor,
            int x, int y, int z, int order) {
        PropertyContainer properties = sprite.getProperties();
        IsoObjectType type = sprite.getType();
        float lift = order * STAND_OFF * 0.1f;

        if (isFloor || properties.has(IsoFlagType.solidfloor) || properties.has(IsoFlagType.FloorOverlay)
                || properties.has(IsoFlagType.attachedFloor)) {
            geometry.addFace(mesh, frame, x, y, z, 0f, 0f, lift / ViewCamera.LEVEL_HEIGHT, 1f, 0f, 0f, 0f, 1f, 0f);
            return;
        }

        boolean west;
        boolean north;
        if (object instanceof IsoDoor door) {
            north = door.north != door.IsOpen();
            west = !north;
        } else if (object instanceof IsoThumpable thumpable && thumpable.isDoor()) {
            north = thumpable.north != thumpable.IsOpen();
            west = !north;
        } else {
            boolean both = properties.has(IsoFlagType.WallNW) || properties.has(IsoFlagType.WallSE);
            west = both || properties.has(IsoFlagType.collideW) || properties.has(IsoFlagType.WallW)
                    || properties.has(IsoFlagType.WallWTrans) || properties.has(IsoFlagType.DoorWallW)
                    || properties.has(IsoFlagType.WindowW) || properties.has(IsoFlagType.windowW)
                    || properties.has(IsoFlagType.doorW) || properties.has(IsoFlagType.cutW)
                    || properties.has(IsoFlagType.HoppableW) || properties.has(IsoFlagType.TallHoppableW)
                    || properties.has(IsoFlagType.attachedW)
                    || type == IsoObjectType.doorFrW || type == IsoObjectType.doorW
                    || type == IsoObjectType.windowFW || type == IsoObjectType.curtainW;
            north = both || properties.has(IsoFlagType.collideN) || properties.has(IsoFlagType.WallN)
                    || properties.has(IsoFlagType.WallNTrans) || properties.has(IsoFlagType.DoorWallN)
                    || properties.has(IsoFlagType.WindowN) || properties.has(IsoFlagType.windowN)
                    || properties.has(IsoFlagType.doorN) || properties.has(IsoFlagType.cutN)
                    || properties.has(IsoFlagType.HoppableN) || properties.has(IsoFlagType.TallHoppableN)
                    || properties.has(IsoFlagType.attachedN)
                    || type == IsoObjectType.doorFrN || type == IsoObjectType.doorN
                    || type == IsoObjectType.windowFN || type == IsoObjectType.curtainN;
        }
        if (west) {
            geometry.addFace(mesh, frame, x, y, z, lift, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f);
        }
        if (north) {
            geometry.addFace(mesh, frame, x, y, z, 0f, lift, 0f, 1f, 0f, 0f, 0f, 0f, 1f);
        }
        if (west || north) {
            return;
        }
        if (properties.has(IsoFlagType.attachedE) || type == IsoObjectType.curtainE) {
            geometry.addFace(mesh, frame, x, y, z, 1f - STAND_OFF, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f);
            return;
        }
        if (properties.has(IsoFlagType.attachedS) || type == IsoObjectType.curtainS) {
            geometry.addFace(mesh, frame, x, y, z, 0f, 1f - STAND_OFF, 0f, 1f, 0f, 0f, 0f, 0f, 1f);
            return;
        }
        geometry.addUpright(mesh, frame, x, y, z, 0.5f, 0.5f, 0f, rightX, rightY);
    }

    private SpriteGeometry.Mesh meshFor(int texture) {
        SpriteGeometry.Mesh mesh = meshByTexture.get(texture);
        if (mesh == null) {
            mesh = new SpriteGeometry.Mesh();
            meshByTexture.put(texture, mesh);
            meshes.add(mesh);
            meshTextures.add(texture);
        }
        return mesh;
    }

    @Override
    public void render() {
        long start = System.nanoTime();
        MouseGrab.sync();

        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL_VIEWPORT, viewport);
        if (!depthChecked) {
            depthChecked = true;
            depthBits = GL11.glGetInteger(GL_DEPTH_BITS);
            Log.info("first person draws into a " + viewport[2] + "x" + viewport[3] + " buffer with "
                    + depthBits + " depth bits, on thread " + Thread.currentThread().getName());
        }

        int program = GL11.glGetInteger(GL_CURRENT_PROGRAM);
        GL11.glPushAttrib(GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadMatrixf(ViewCamera.projection(FIELD_OF_VIEW, (float) viewport[2] / viewport[3], NEAR, FAR));
        GL11.glMatrixMode(GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadMatrixf(ViewCamera.view(eyeX, eyeY, eyeUp, yaw, pitch));
        GL20.glUseProgram(0);
        GL13.glActiveTexture(GL_TEXTURE0);

        GL11.glDisable(GL_SCISSOR_TEST);
        GL11.glDisable(GL_STENCIL_TEST);
        GL11.glDisable(GL_CULL_FACE);
        GL11.glDisable(GL_BLEND);
        GL11.glDisable(GL_LIGHTING);
        GL11.glDisable(GL_FOG);
        GL11.glColorMask(true, true, true, true);
        GL11.glDepthMask(true);
        GL11.glClearColor(0f, 0f, 0f, 1f);
        GL11.glClearDepth(1.0);
        GL11.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        GL11.glEnable(GL_DEPTH_TEST);
        GL11.glDepthFunc(GL_LEQUAL);
        GL11.glEnable(GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL_GREATER, 0.5f);
        GL11.glEnable(GL_TEXTURE_2D);

        for (int i = 0; i < meshes.size(); i++) {
            SpriteGeometry.Mesh mesh = meshes.get(i);
            if (mesh.floats == 0) {
                continue;
            }
            GL11.glBindTexture(GL_TEXTURE_2D, meshTextures.get(i));
            float[] v = mesh.vertices;
            GL11.glBegin(GL_TRIANGLES);
            for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
                GL11.glColor3f(v[at + 5], v[at + 6], v[at + 7]);
                GL11.glTexCoord2f(v[at + 3], v[at + 4]);
                GL11.glVertex3f(v[at], v[at + 1], v[at + 2]);
            }
            GL11.glEnd();
        }

        GL20.glUseProgram(program);
        GL11.glMatrixMode(GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glPopAttrib();

        int error = GL11.glGetError();
        if (error != 0) {
            throw new IllegalStateException("OpenGL error 0x" + Integer.toHexString(error) + " drawing the first-person view");
        }
        lastDrawMillis = (System.nanoTime() - start) / 1e6f;
    }
}
