package zombie.chat;

import zombie.ui.TextDrawObject;

public class ChatElement {
    protected PlayerLines[] playerLines;
    protected float historyVal;
    public static boolean doBackDrop;
    public static NineGridTexture backdropTexture;
    public boolean getHasChatToDisplay() { throw new UnsupportedOperationException("reference stub"); }
    public void renderBatched(int playerIndex, int x, int y) { throw new UnsupportedOperationException("reference stub"); }
    public void renderBatched(int playerIndex, int x, int y, boolean ignoreRadioLines) { throw new UnsupportedOperationException("reference stub"); }

    class PlayerLines {
        protected int lineDisplayTime;
        protected TextDrawObject[] chatLines;
    }
}
