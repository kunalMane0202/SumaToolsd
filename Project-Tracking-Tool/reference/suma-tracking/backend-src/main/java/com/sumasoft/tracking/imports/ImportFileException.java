package com.sumasoft.tracking.imports;

/**
 * The whole file cannot be imported (wrong type, unreadable, required columns missing, too many rows...).
 * The message is shown to the user, so it never contains internal details.
 */
public class ImportFileException extends RuntimeException {

    public ImportFileException(String message) {
        super(message);
    }
}