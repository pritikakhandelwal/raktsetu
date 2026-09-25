package com.raktsetu.domain.exception;

/** A row in a data file is malformed. The loader reports it and continues with the other rows. */
public class InvalidDataException extends RaktSetuException {

    private static final long serialVersionUID = 1L;

    public InvalidDataException(String source, int lineNumber, String problem) {
        super(source + ", line " + lineNumber + ": " + problem);
    }

    public InvalidDataException(String source, int lineNumber, String problem, Throwable cause) {
        super(source + ", line " + lineNumber + ": " + problem, cause);
    }
}
