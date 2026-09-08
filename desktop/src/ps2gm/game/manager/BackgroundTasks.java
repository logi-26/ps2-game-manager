package ps2gm.game.manager;

/**
 * Runs a short-lived background task without blocking the caller.
 *
 * Uses a virtual thread per task (JEP 444) rather than a shared pooled
 * executor - the JDK's own guidance is not to pool virtual threads, they're
 * cheap enough to create one per task. Virtual threads are always daemon,
 * so this is only a drop-in replacement for callers that already used
 * {@code new Thread(...).setDaemon(true)}.
 */
public final class BackgroundTasks {

    private BackgroundTasks() {}

    public static void runDaemon(String name, Runnable task) {
        Thread.ofVirtual().name(name).start(task);
    }
}
