package com.dista.org.cap.media;

import com.dista.org.cap.R;
import com.dista.org.cap.exception.AVException;
import com.dista.org.cap.util.Util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Vector;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by dista on 2015/8/12.
 */
public class Flv {
    private byte[] sps;
    private byte[] pps;

    public RtmpAVPacket getAvcHeader() {
        return avcHeader;
    }

    private RtmpAVPacket avcHeader;
    private ConcurrentLinkedQueue<RtmpAVPacket> pkts;

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
}
