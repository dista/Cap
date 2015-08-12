package com.dista.org.cap.media;

/**
 * Created by dista on 2015/8/7.
 */
public class AVMetaData {
    public boolean hasVideo;
    public boolean hasAudio;
    public String encoder;
    // MediaFormat
    public String videoMIMEType;
    public int videoWidth;
    public int videoHeight;
    public int videoDataRate;
    public int videoFrameRate;

    public String audioMIMEType;
    public int audioSampleRate;
    public int audioChannels;
    public int audioDataRate;
    // TODO: more fields
}