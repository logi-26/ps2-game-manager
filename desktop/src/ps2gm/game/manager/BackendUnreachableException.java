package ps2gm.game.manager;

public final class BackendUnreachableException extends RuntimeException {
    public BackendUnreachableException(String message, Throwable cause) {
        super(message, cause);
    }
}
