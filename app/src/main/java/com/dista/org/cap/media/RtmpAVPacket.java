package com.dista.org.cap.media;

/**
 * Created by dista on 2015/8/12.
 */
public class RtmpAVPacket {
    public int avType; // 1: audio, 2: video
    public long dts;
    public long pts;
    public byte[] data;
}
