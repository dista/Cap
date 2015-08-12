package com.dista.org.cap.exception;

/**
 * Created by dista on 2015/8/12.
 */
public class AVException extends Exception{
    public AVException() {
        super();
    }

    public AVException(String detailMessage) {
        super(detailMessage);
    }

    public AVException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

    public AVException(Throwable throwable) {
        super(throwable);
    }
}
