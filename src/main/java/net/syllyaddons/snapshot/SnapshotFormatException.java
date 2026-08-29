package net.syllyaddons.snapshot;

public final class SnapshotFormatException extends Exception {
    public SnapshotFormatException(String message) {
        super(message);
    }

    public SnapshotFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
