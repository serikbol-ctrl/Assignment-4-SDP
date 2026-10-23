package edu.registration.facade;

/** The client's request is unusable (blank input, already enrolled, unknown chain). Nothing was committed. */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}
