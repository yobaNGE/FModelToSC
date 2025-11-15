package com.pipemasters.app;

public class LayerExportException extends RuntimeException {
    public LayerExportException(String message) {
        super(message);
    }

    public LayerExportException(String message, Throwable cause) {
        super(message, cause);
    }
}
