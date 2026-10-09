package com.cameraunlock.zomboid;

import zombie.network.GameClient;
import zombie.network.ServerOptions;

/** Puts a test in a game alone, on a server that lets players fight, or on one that does not, and back alone after. */
final class Servers {
    private Servers() {}

    enum Where { ALONE, FRIENDLY, CONTESTED }

    interface Test {
        void run();
    }

    static void in(Where where, Test test) {
        GameClient.client = where != Where.ALONE;
        ServerOptions.getInstance().pvp.setValue(where == Where.CONTESTED);
        try {
            test.run();
        } finally {
            GameClient.client = false;
            ServerOptions.getInstance().pvp.setValue(false);
        }
    }
}
