package ps2gm.game.manager;

/**
 * Thrown by a {@link BackendClient} method when the API genuinely could not be
 * reached (connection refused, timed out, DNS failure, etc.) - as opposed to a
 * normal HTTP response reporting that the requested thing doesn't exist, which
 * is expressed as a null/absent result instead. Callers that only care about
 * "is there something to show" can ignore this distinction; {@link
 * AppUpdateChecker} uses it to tell a real connectivity problem apart from
 * there simply being no release published yet.
 */
public final class BackendUnreachableException extends RuntimeException {
    public BackendUnreachableException(String message, Throwable cause) {
        super(message, cause);
    }
}
