package ps2gm.game.manager;

/**
 * Runs a short-lived background task without blocking the caller.
 *
 * Uses a virtual thread per task rather than a shared pooled executor
 * the JDK's own guidance is not to pool virtual threads, they're
 * cheap enough to create one per task.
 */
public final class BackgroundTasks {

    private BackgroundTasks() {}

    public static void runDaemon(String name, Runnable task) {
        Thread.ofVirtual().name(name).start(task);
    }
}
