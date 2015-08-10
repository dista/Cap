package com.dista.org.cap.exception;

/**
 * Created by dista on 2015/8/10.
 */
public class RtmpException extends Exception{
    public RtmpException() {
        super();
    }

    public RtmpException(String detailMessage) {
        super(detailMessage);
    }

    public RtmpException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

    public RtmpException(Throwable throwable) {
        super(throwable);
    }
}
