package com.dista.org.cap.proto;

/**
 * Created by dista on 2015/8/10.
 */
public class RtmpMsg {
    public RtmpMsgHeader getHeader() {
        return header;
    }

    public void setHeader(RtmpMsgHeader header) {
        this.header = header;
    }

    private RtmpMsgHeader header;

    public RtmpMsg(){
        header = new RtmpMsgHeader();
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    private byte[] data;
}
