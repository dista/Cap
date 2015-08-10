package com.dista.org.cap.media;

/**
 * Created by dista on 2015/8/7.
 */
public class AVPacket {
    public int avType; // 1: audio, 2: video
    public long dts;
    public long pts;
    public int flags; // 1: keyframe
    public int avcTypes; // 0 avc sequence header, 1 avc NALU, 2 AVC sequence ender
    public byte[] data;
    public int aacTypes; // 0 aac sequence header, 2 aac raw
}
