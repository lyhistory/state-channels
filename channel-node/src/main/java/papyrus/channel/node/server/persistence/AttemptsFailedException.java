package papyrus.channel.node.server.persistence;

public class AttemptsFailedException extends RuntimeException {
    public AttemptsFailedException() {
    }

    public AttemptsFailedException(String message) {
        super(message);
    }

    public AttemptsFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    public AttemptsFailedException(Throwable cause) {
        super(cause);
    }

    public AttemptsFailedException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
