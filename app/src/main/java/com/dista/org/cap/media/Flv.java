package com.dista.org.cap.media;

import android.util.Log;

import com.dista.org.cap.R;
import com.dista.org.cap.exception.AVException;
import com.dista.org.cap.util.Util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Vector;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by dista on 2015/8/12.
 */
public class Flv {
    private static final int[] AAC_SAMPLE_RATES = {96000, 88200, 64000, 48000, 44100, 32000,
                                    24000, 22050, 16000, 12000, 11025, 8000, 7350
                                };
    private static final byte[] AAC_CHANNELS = {0, 1, 2, 3, 4, 5, 6, 8};

    private byte[] sps;
    private byte[] pps;

    public RtmpAVPacket getAvcHeader() {
        return avcHeader;
    }

    private RtmpAVPacket avcHeader;
    private RtmpAVPacket aacHeader;
    private ConcurrentLinkedQueue<RtmpAVPacket> pkts;
    private int channel;

    public Flv(){
        pkts = new ConcurrentLinkedQueue<RtmpAVPacket>();
    }

    private void buildAVCHeader() throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        Util.OutStreamWriteInt(os, true, 0x17, 1);
        Util.OutStreamWriteInt(os, true, 0, 4);

        //start sps, pps
        //version
        Util.OutStreamWriteInt(os, true, 1, 1);
        Util.OutStreamWriteInt(os, true, sps[1] & 0xFF, 1);
        Util.OutStreamWriteInt(os, true, sps[2] & 0xFF, 1);
        Util.OutStreamWriteInt(os, true, sps[3] & 0xFF, 1);
        Util.OutStreamWriteInt(os, true, 0xFFE1, 2);
        Util.OutStreamWriteInt(os, true, sps.length, 2);
        os.write(sps);

        Util.OutStreamWriteInt(os, true, 1, 1);
        Util.OutStreamWriteInt(os, true, pps.length, 2);
        os.write(pps);

        RtmpAVPacket pkt = new RtmpAVPacket();
        pkt.avType = 2;
        pkt.data = os.toByteArray();

        this.avcHeader = pkt;
    }

    public void SetAudioChannel(int channel) {
        this.channel = channel;
    }

    public RtmpAVPacket buildAACHeaderExternalParams(int profile, int sfi, int cfg, long timestamp) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        Util.OutStreamWriteInt(os, true, 0xAF00, 2);
        int hd = (profile << 11) +
                (sfi << 7) +
                (cfg << 3);
        Util.OutStreamWriteInt(os, true, hd, 2);

        RtmpAVPacket pkt = new RtmpAVPacket();
        pkt.avType = 1;
        pkt.data = os.toByteArray();
        pkt.dts = timestamp / 1000;
        pkt.pts = timestamp / 1000;

        return pkt;
    }

    private void buildAACHeader(AdtsHeader adts) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        Util.OutStreamWriteInt(os, true, 0xAF00, 2);
        int hd = ((int)adts.getProfile() << 11) +
                ((int)adts.getSamplingFrequencyIndex() << 7) +
                ((int)adts.getChannelConfiguration() << 3);
        Util.OutStreamWriteInt(os, true, hd, 2);

        // TODO: no pce now.

        RtmpAVPacket pkt = new RtmpAVPacket();
        pkt.avType = 1;
        pkt.data = os.toByteArray();
        this.aacHeader = pkt;
    }

    private RtmpAVPacket buildAVC(byte[] nal, int start, int end, long dts, long pts) throws IOException {
        int nalType = nal[start + 3] & 0x1F;

        ByteArrayOutputStream os = new ByteArrayOutputStream();

        if(nalType == 5){
            Util.OutStreamWriteInt(os, true, 0x17, 1);
        } else {
            Util.OutStreamWriteInt(os, true, 0x27, 1);
        }

        Util.OutStreamWriteInt(os, true, 1, 1);
        long cts = (pts - dts) / 1000;
        Util.OutStreamWriteInt(os, true, cts, 3);

        // avc1
        Util.OutStreamWriteInt(os, true, end - start - 3, 4);
        os.write(nal, start + 3, end - (start + 3));

        RtmpAVPacket pkt = new RtmpAVPacket();
        pkt.avType = 2;

        pkt.data = os.toByteArray();
        pkt.dts = dts / 1000;
        pkt.pts = pts / 1000;

        return pkt;
    }

    private RtmpAVPacket buildAudioTag(int format, byte[] buf, int start, int end, long dts, long pts) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        int soundFormat = format;
        int soundRate = 3;
        int soundType = 1;

        if (format == 9) {
            if (this.channel == 1) {
                soundType = 0;
            }
        }

        int tmp = (soundFormat << 4) + (soundRate << 2) + 0x2
                + soundType;
        Util.OutStreamWriteInt(os, true, tmp, 1);
        if (format == 10) {
            Util.OutStreamWriteInt(os, true, 1, 1);
        }

        os.write(buf, start, end - start);

        RtmpAVPacket pkt = new RtmpAVPacket();
        pkt.avType = 1;

        pkt.data = os.toByteArray();
        pkt.dts = dts / 1000;
        pkt.pts = pts / 1000;

        return pkt;
    }

    private void handleOneNal(byte[] nal, int start, int end,
                              long dts, long pts) throws AVException {
        int nalType = nal[start + 3] & 0x1F;

        if(nalType == 7){
            sps = Arrays.copyOfRange(nal, start + 3, end);
        } else if(nalType == 8){
            pps = Arrays.copyOfRange(nal, start + 3, end);

            try {
                buildAVCHeader();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if(nalType == 5 || nalType == 1){
            // nal 5: key frame
            // nal 1: normal frame

            if(avcHeader == null){
                throw new AVException("avc header not found");
            }

            RtmpAVPacket pkt = null;
            try {
                pkt = buildAVC(nal, start, end, dts, pts);
                pkts.add(pkt);
            } catch (IOException e) {
                // should not happen
                e.printStackTrace();
            }
        }
    }

    private AdtsHeader parseAdtsHeader(ByteBuffer bf) throws AVException {
        AdtsHeader h = new AdtsHeader();

        int tmp = (int) Util.readLongFromByteBuffer(bf, true, 2);

        if((tmp >> 4) != 0xFFF){
            throw new AVException("Not ADTS");
        }

        h.setId((byte) ((tmp >> 3) & 0x01));
        h.setLayer((byte) ((tmp >> 1) & 0x03));
        h.setProtectionAbsent((byte) (tmp & 0x01));

        tmp = (int) Util.readLongFromByteBuffer(bf, true, 2);
        int tmp2 = (int) Util.readLongFromByteBuffer(bf, true, 3);

        h.setProfile((byte) (((tmp >> 14) & 0x3) + 1));
        h.setSamplingFrequencyIndex((byte) ((tmp >> 10) & 0xF));
        h.setSampleRate(AAC_SAMPLE_RATES[h.getSamplingFrequencyIndex()]);
        h.setPrivateBit((byte) ((tmp >> 9) & 0x1));
        h.setChannelConfiguration((byte) ((tmp >> 6) & 0x7));
        h.setOriginalCopy((byte) ((tmp >> 5) & 0x1));
        h.setHome((byte) ((tmp >> 4) & 0x1));
        h.setCopyrightBit((byte) ((tmp >> 3) & 0x1));
        h.setCopyrightStart((byte) ((tmp >> 2) & 0x1));

        h.setFrameLength((short) (((tmp & 0x3) << 11)
                + ((tmp2 >> 13) & 0x7FF)));
        h.setAdtsBufferFullness((short) ((tmp2 >> 2) & 0x7FF));
        h.setNumberOfRawDataBlocksInFrame((byte) ((tmp2 & 0x3) + 1));
        h.setBitRate(h.getFrameLength() * 8 * h.getSampleRate() /
            h.getNumberOfRawDataBlocksInFrame() * 1024);
        h.setSamples(h.getNumberOfRawDataBlocksInFrame() * 1024);

        if(h.getProtectionAbsent() == 0){
            // SKIP
            bf.position(bf.position() + 2);
        }

        if(h.getChannelConfiguration() == 0){
            throw new AVException("pce not supported");
        }

        return h;
    }

    public void feedRaw(AudioCodec codec, byte[] raw, long dts, long pts){
        RtmpAVPacket pkt = null;
        try {
            //AudioCodec.AAC
            int format = 10;
            if (codec == AudioCodec.OPUS) {
                format = 9;
            }

            pkt = buildAudioTag(format, raw, 0, raw.length, dts, pts);
            pkts.add(pkt);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void feedADTS(byte[] adts, long dts, long pts) throws AVException, IOException {
        ByteBuffer bf = ByteBuffer.wrap(adts);

        AdtsHeader h = parseAdtsHeader(bf);

        if(aacHeader == null){
            buildAACHeader(h);
        }

        RtmpAVPacket pkt = null;
        try {
            pkt = buildAudioTag(10, adts, bf.position(), bf.limit(), dts, pts);
            pkts.add(pkt);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void feedNal(byte[] nal, long dts, long pts) throws AVException {
        int offset = 0;
        while(offset < nal.length) {
            int start = NalUtil.findStartCode(nal, offset);

            if (start == -1) {
                throw new AVException("start code is not found");
            }

            int end = NalUtil.findStartCode(nal, start + 2);
            if(end == -1){
                end = nal.length;
                offset = end;
            } else {
                offset = end;

                if(nal[end - 1] == 0){
                    while(nal[--end] == 0){}
                    end++;
                }
            }

            handleOneNal(nal, start, end, dts, pts);
        }
    }

    public ConcurrentLinkedQueue<RtmpAVPacket> getPkts() {
        return pkts;
    }

    public RtmpAVPacket getAacHeader() {
        return aacHeader;
    }
}
