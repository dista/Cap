package com.dista.org.cap.proto;

/**
 * Created by dista on 2015/8/10.
 */
public class RtmpMsgHeader {
    public int getFmt() {
        return fmt;
    }

    public void setFmt(int fmt) {
        this.fmt = fmt;
    }

    int fmt;

    public int getCsId() {
        return csId;
    }

    public void setCsId(int csId) {
        this.csId = csId;
    }

    int csId;

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    long timestamp;

    public int getMessageLen() {
        return messageLen;
    }

    public void setMessageLen(int messageLen) {
        this.messageLen = messageLen;
    }

    int messageLen;

    public short getMsgTypeId() {
        return msgTypeId;
    }

    public void setMsgTypeId(short msgTypeId) {
        this.msgTypeId = msgTypeId;
    }

    short msgTypeId;

    public int getMsgStreamId() {
        return msgStreamId;
    }

    public void setMsgStreamId(int msgStreamId) {
        this.msgStreamId = msgStreamId;
    }

    int msgStreamId;

    public int getExtendTimestamp() {
        return extendTimestamp;
    }

    public void setExtendTimestamp(int extendTimestamp) {
        this.extendTimestamp = extendTimestamp;
    }

    int extendTimestamp;

    public long getCalTimestamp() {
        return calTimestamp;
    }

    public void setCalTimestamp(long calTimestamp) {
        this.calTimestamp = calTimestamp;
    }

    long calTimestamp;
}
