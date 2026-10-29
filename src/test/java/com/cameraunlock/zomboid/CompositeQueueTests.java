package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Tests of which composites the render thread makes while it waits for a frame: see CompositeQueue. */
final class CompositeQueueTests {
    private CompositeQueueTests() {}

    static void run() {
        Checks.run("whatIsAskedIsMadeInTheWaitInTheOrderAsked", CompositeQueueTests::whatIsAskedIsMadeInTheWaitInTheOrderAsked);
        Checks.run("aHandOverStopsTheMakingAfterTheOneInHand", CompositeQueueTests::aHandOverStopsTheMakingAfterTheOneInHand);
        Checks.run("aThreadComingToWakeTheRenderThreadStopsTheMakingAfterTheOneInHand",
                CompositeQueueTests::aThreadComingToWakeTheRenderThreadStopsTheMakingAfterTheOneInHand);
        Checks.run("nothingAskedIsCarriedPastItsFrame", CompositeQueueTests::nothingAskedIsCarriedPastItsFrame);
        Checks.run("anAskWhileTheRenderThreadLooksIsMadeWithoutAWake", CompositeQueueTests::anAskWhileTheRenderThreadLooksIsMadeWithoutAWake);
        Checks.run("anAskAsTheRenderThreadFindsNoneLeftIsMadeBeforeItSleeps", CompositeQueueTests::anAskAsTheRenderThreadFindsNoneLeftIsMadeBeforeItSleeps);
        Checks.run("noneIsMadeWhileTheLogicThreadWaitsToWakeTheRenderThread", CompositeQueueTests::noneIsMadeWhileTheLogicThreadWaitsToWakeTheRenderThread);
        Checks.run("oneMadeAlreadyIsPassedOver", CompositeQueueTests::oneMadeAlreadyIsPassedOver);
        Checks.run("oneWhosePicturesAreNotReadyIsLeftToTheDraw", CompositeQueueTests::oneWhosePicturesAreNotReadyIsLeftToTheDraw);
        Checks.run("noneIsMadeOnceTheGameLetsGoOfTheFramesDraws", CompositeQueueTests::noneIsMadeOnceTheGameLetsGoOfTheFramesDraws);
        Checks.run("theGameLetsGoOfAFramesDrawsOnlyOnceTheOneInHandIsMade", CompositeQueueTests::theGameLetsGoOfAFramesDrawsOnlyOnceTheOneInHandIsMade);
        Checks.run("noneIsMadeOnceTheWaitEnds", CompositeQueueTests::noneIsMadeOnceTheWaitEnds);
    }

    /** The game's side, with composites named: what is made in what order, and how often the render thread is woken. */
    private static final class Game {
        final Set<String> made = new HashSet<>(), unready = new HashSet<>();
        final List<String> order = new ArrayList<>();
        int wakes;
        /** What the other thread does while one is made, and while the logic thread waits for the lock to wake the render thread. */
        Runnable duringMake = () -> {}, duringWake = () -> {};
        final CompositeQueue<String> queue;

        Game() {
            this(new ConcurrentLinkedQueue<>());
        }

        Game(ConcurrentLinkedQueue<String> asks) {
            queue = new CompositeQueue<>(asks, made::contains, this::make, this::wake);
        }

        private void make(String composite) {
            duringMake.run();
            if (!unready.contains(composite)) {
                made.add(composite);
                order.add(composite);
            }
        }

        private void wake() {
            queue.notifying();
            wakes++;
            duringWake.run();
            queue.woken();
        }

        /** The render thread woken without a frame: the game's callback, and what is made as it ends. */
        void callback(boolean waiting) {
            queue.calling();
            for (int rounds = 0; queue.toMake(waiting); rounds++) {
                // A round makes one at least or is the last, and no test asks for more than a few in a callback: a tenth round goes round for ever.
                if (rounds == 10) {
                    check(false, "the callback, which the render thread runs holding the lock on the frames, ends once none is left to make or another thread wants the lock");
                    return;
                }
                queue.make();
            }
        }
    }

    /** The asks as the queue keeps them, and one more, asked for by the logic thread as the render thread next finds none left. */
    private static final class AskedAsFoundEmpty extends ConcurrentLinkedQueue<String> {
        private static final long serialVersionUID = 1L;
        transient Runnable ask;

        @Override
        public boolean isEmpty() {
            boolean empty = super.isEmpty();
            Runnable now = ask;
            if (empty && now != null) {
                ask = null;
                now.run();
            }
            return empty;
        }
    }

    private static void whatIsAskedIsMadeInTheWaitInTheOrderAsked() {
        Game game = new Game();
        game.queue.ask("a");
        game.queue.ask("b");
        game.queue.ask("c");
        check(game.wakes == 3, "each ask wakes the render thread while it sleeps: " + game.wakes + " wakes");
        game.callback(true);
        check(game.order.equals(List.of("a", "b", "c")), "the render thread makes them in its wait, in the order asked: " + game.order);
        game.queue.ask("d");
        check(game.wakes == 4, "and sleeps again once they are made, so the next ask wakes it: " + game.wakes + " wakes");
    }

    private static void aHandOverStopsTheMakingAfterTheOneInHand() {
        Game game = new Game();
        game.queue.ask("a");
        game.queue.ask("b");
        game.queue.ask("c");
        // The logic thread comes to hand the frame over while the render thread makes the first.
        game.duringMake = game.queue::handing;
        game.callback(true);
        check(game.order.equals(List.of("a")), "the one in hand is finished and no other begun: " + game.order);
        game.queue.handed();
        String tolls = game.queue.tolls();
        check(tolls.contains("left to the draw 2,"), "the other two are left to the frame's draw: " + tolls);
    }

    private static void aThreadComingToWakeTheRenderThreadStopsTheMakingAfterTheOneInHand() {
        Game game = new Game();
        game.queue.ask("a");
        game.queue.ask("b");
        game.queue.ask("c");
        // Another thread comes to wake the render thread, to have it run something of its own, while the first is made.
        game.duringMake = () -> {
            if (game.order.isEmpty()) {
                game.queue.notifying();
            }
        };
        game.callback(true);
        check(game.order.equals(List.of("a")), "the one in hand is finished and the lock let go of: " + game.order);
        game.queue.woken();
        game.callback(true);
        check(game.order.equals(List.of("a", "b", "c")), "and the rest are made once that thread has woken it: " + game.order);
    }

    private static void nothingAskedIsCarriedPastItsFrame() {
        Game game = new Game();
        game.queue.ask("a");
        game.queue.handing();
        game.callback(true);
        check(game.order.isEmpty(), "none is begun once the logic thread has begun to hand the frame over: " + game.order);
        game.queue.handed();
        game.queue.ask("b");
        game.callback(true);
        check(game.order.equals(List.of("b")), "the next frame's are made in its wait, and the frame before's were its draw's: " + game.order);
    }

    private static void anAskWhileTheRenderThreadLooksIsMadeWithoutAWake() {
        Game game = new Game();
        game.queue.ask("a");
        game.duringMake = () -> {
            if (game.order.isEmpty()) {
                game.queue.ask("b");
            }
        };
        game.callback(true);
        check(game.wakes == 1, "an ask while the render thread is in its callback does not wake it: " + game.wakes + " wakes");
        check(game.order.equals(List.of("a", "b")), "it is made before the render thread sleeps: " + game.order);
    }

    private static void anAskAsTheRenderThreadFindsNoneLeftIsMadeBeforeItSleeps() {
        AskedAsFoundEmpty asks = new AskedAsFoundEmpty();
        Game game = new Game(asks);
        game.queue.ask("a");
        // The logic thread asks for b as the render thread, done with a, finds none left, before the render thread lets it know
        // that the next ask has to wake it: the ask sees the render thread still looking and does not wake it.
        game.duringMake = () -> {
            if (game.order.isEmpty()) {
                asks.ask = () -> game.queue.ask("b");
            }
        };
        game.callback(true);
        check(game.wakes == 1, "an ask in that moment does not wake the render thread: " + game.wakes + " wakes");
        check(game.order.equals(List.of("a", "b")), "the render thread looks once more before it sleeps, and makes it: " + game.order);
    }

    private static void noneIsMadeWhileTheLogicThreadWaitsToWakeTheRenderThread() {
        Game game = new Game();
        List<String> madeMeanwhile = new ArrayList<>();
        // The render thread is in its callback with the lock as the logic thread comes for it to wake it.
        game.duringWake = () -> {
            game.callback(true);
            madeMeanwhile.addAll(game.order);
        };
        game.queue.ask("a");
        check(madeMeanwhile.isEmpty(), "the render thread lets go of the lock without making any: " + madeMeanwhile);
        game.duringWake = () -> {};
        game.callback(true);
        check(game.order.equals(List.of("a")), "and makes it once woken: " + game.order);
    }

    private static void oneMadeAlreadyIsPassedOver() {
        Game game = new Game();
        game.queue.ask("a");
        game.queue.ask("a");
        game.callback(true);
        check(game.order.equals(List.of("a")), "one asked for twice, by a model drawn twice in a frame, is made once: " + game.order);
        game.made.add("b");
        game.queue.ask("b");
        game.callback(true);
        check(game.order.equals(List.of("a")), "and one made already is not made again: " + game.order);
    }

    private static void oneWhosePicturesAreNotReadyIsLeftToTheDraw() {
        Game game = new Game();
        game.unready.add("a");
        game.queue.ask("a");
        game.queue.ask("b");
        game.callback(true);
        check(game.order.equals(List.of("b")), "one whose pictures are still loading is tried once and the rest made: " + game.order);
        game.unready.clear();
        game.callback(true);
        check(game.order.equals(List.of("b")), "and it is not tried again in the wait, but left to the frame's draw: " + game.order);
    }

    private static void noneIsMadeOnceTheGameLetsGoOfTheFramesDraws() {
        Game game = new Game();
        game.queue.ask("a");
        game.queue.lettingGo();
        game.callback(true);
        check(game.order.isEmpty(), "one whose frame's draws the game let go of is not made, as the game may have taken it back: " + game.order);
        game.queue.ask("b");
        game.callback(true);
        check(game.order.equals(List.of("b")), "and one asked for after is: " + game.order);
    }

    private static void theGameLetsGoOfAFramesDrawsOnlyOnceTheOneInHandIsMade() throws InterruptedException {
        Game game = new Game();
        CountDownLatch inHand = new CountDownLatch(1), finish = new CountDownLatch(1);
        game.duringMake = () -> {
            inHand.countDown();
            try {
                finish.await();
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        };
        game.queue.ask("a");
        game.queue.ask("b");
        Thread render = new Thread(() -> game.callback(true));
        render.start();
        Thread logic = new Thread(game.queue::lettingGo);
        try {
            check(inHand.await(10, TimeUnit.SECONDS), "the render thread begins to make the first in its wait");
            // Under the Lua debugger the game lets go of the frame being drawn up, whose composites the render thread may be making.
            logic.start();
            logic.join(200);
            check(logic.isAlive(), "the logic thread waits to let go of the frame's draws while the render thread makes one of its composites");
        } finally {
            finish.countDown();
        }
        logic.join();
        render.join();
        check(game.order.equals(List.of("a")), "and lets go of them once that one is made, with the rest not begun: " + game.order);
    }

    private static void noneIsMadeOnceTheWaitEnds() {
        Game game = new Game();
        game.queue.ask("a");
        game.callback(false);
        check(game.order.isEmpty(), "the render thread leaving its wait with no frame makes none: " + game.order);
        game.queue.ask("b");
        check(game.wakes == 2, "and the next ask wakes it: " + game.wakes + " wakes");
    }
}
