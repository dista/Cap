package com.dista.org.cap.media;

/**
 * Created by dista on 2015/8/13.
 */
public class AdtsHeader {
    public byte getId() {
        return id;
    }

    public void setId(byte id) {
        this.id = id;
    }

    public byte getLayer() {
        return layer;
    }

    public void setLayer(byte layer) {
        this.layer = layer;
    }

    public byte getProtectionAbsent() {
        return protectionAbsent;
    }

    public void setProtectionAbsent(byte protectionAbsent) {
        this.protectionAbsent = protectionAbsent;
    }

    public byte getProfile() {
        return profile;
    }

    public void setProfile(byte profile) {
        this.profile = profile;
    }

    public byte getSamplingFrequencyIndex() {
        return samplingFrequencyIndex;
    }

    public void setSamplingFrequencyIndex(byte samplingFrequencyIndex) {
        this.samplingFrequencyIndex = samplingFrequencyIndex;
    }

    public byte getPrivateBit() {
        return privateBit;
    }

    public void setPrivateBit(byte privateBit) {
        this.privateBit = privateBit;
    }

    public byte getChannelConfiguration() {
        return channelConfiguration;
    }

    public void setChannelConfiguration(byte channelConfiguration) {
        this.channelConfiguration = channelConfiguration;
    }

    public byte getOriginalCopy() {
        return originalCopy;
    }

    public void setOriginalCopy(byte originalCopy) {
        this.originalCopy = originalCopy;
    }

    public byte getHome() {
        return home;
    }

    public void setHome(byte home) {
        this.home = home;
    }

    public byte getCopyrightBit() {
        return copyrightBit;
    }

    public void setCopyrightBit(byte copyrightBit) {
        this.copyrightBit = copyrightBit;
    }

    public byte getCopyrightStart() {
        return copyrightStart;
    }

    public void setCopyrightStart(byte copyrightStart) {
        this.copyrightStart = copyrightStart;
    }

    public short getFrameLength() {
        return frameLength;
    }

    public void setFrameLength(short frameLength) {
        this.frameLength = frameLength;
    }

    public short getAdtsBufferFullness() {
        return adtsBufferFullness;
    }

    public void setAdtsBufferFullness(short adtsBufferFullness) {
        this.adtsBufferFullness = adtsBufferFullness;
    }

    public byte getNumberOfRawDataBlocksInFrame() {
        return numberOfRawDataBlocksInFrame;
    }

    public void setNumberOfRawDataBlocksInFrame(byte numberOfRawDataBlocksInFrame) {
        this.numberOfRawDataBlocksInFrame = numberOfRawDataBlocksInFrame;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
    }

    public int getBitRate() {
        return bitRate;
    }

    public void setBitRate(int bitRate) {
        this.bitRate = bitRate;
    }

    public int getSamples() {
        return samples;
    }

    public void setSamples(int samples) {
        this.samples = samples;
    }

    private byte id;
    private byte layer;
    private byte protectionAbsent;
    private byte profile;
    private byte samplingFrequencyIndex;
    private byte privateBit;
    private byte channelConfiguration;
    private byte originalCopy;
    private byte home;
    private byte copyrightBit;
    private byte copyrightStart;
    private short frameLength;
    private short adtsBufferFullness;
    private byte numberOfRawDataBlocksInFrame;
    private int sampleRate;
    private int bitRate;
    private int samples;
}
