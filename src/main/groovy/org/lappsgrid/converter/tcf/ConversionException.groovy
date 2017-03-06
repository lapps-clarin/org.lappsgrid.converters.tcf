package org.lappsgrid.converter.tcf

/**
 * @author Keith Suderman
 */
class ConversionException extends Exception {
    ConversionException() {
        super()
    }

    ConversionException(String message) {
        super(message)
    }

    ConversionException(String message, Throwable cause) {
        super(message, cause)
    }

    ConversionException(Throwable cause) {
        super(cause)
    }

    protected ConversionException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace)
    }
}
