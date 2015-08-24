package com.dista.org.cap.proto;

import com.dista.org.cap.exception.RtmpException;
import com.dista.org.cap.util.Util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Vector;

/**
 * Created by dista on 2015/8/10.
 */
public class RtmpDecoder {
    private class RtmpDecoderStream {
        private RtmpMsgHeader header;
        private long rTimeDelta;
        private long rMessageLen;
        private long rMsgTypeId;
        private long rMsgStreamId;
        private long rTimestamp;
        private long rExtendTimestamp;
        private long rCalTimestamp;
    }

    private int chunkSize;

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    private InputStream input;

    public InputStream getInput() {
        return input;
    }

    public void setInput(InputStream input) {
        this.input = input;
    }

    public RtmpDecoder(){
        this.chunkSize = 128;
        this.ds = new Vector<RtmpDecoderStream>();
    }

    private Vector<RtmpDecoderStream> ds;

    private RtmpDecoderStream findDecoderStream(int csId){
        for (RtmpDecoderStream rd : ds
             ) {
            if(rd.header.getCsId() == csId){
                return rd;
            }
        }
        return null;
    }

    private RtmpDecoderStream addDecoderStream(RtmpMsgHeader h){
        RtmpDecoderStream rd = new RtmpDecoderStream();
        rd.header = h;

        ds.add(rd);

        return rd;
    }

    public boolean hasData() throws IOException {
        return (this.input.available() != 0);
    }

    public RtmpMsg decode() throws IOException, RtmpException {
        long tmp;

        RtmpMsg msg = new RtmpMsg();
        RtmpMsgHeader h = msg.getHeader();
        int bodyRead = 0;

        while(true) {

            tmp = Util.readLongFromInput(input, true, 1);
            h.setFmt((int) (tmp >> 6));
            h.setCsId((int) (tmp & ((1 << 6) - 1)));

            // FMT_DONE
            if (h.getCsId() <= 1) {
                if (h.getCsId() == 0) {
                    h.setCsId((int) (64 + Util.readLongFromInput(input, true, 1)));
                } else { // csId == 1
                    h.setCsId((int) (64 + Util.readLongFromInput(input, true, 2)));
                }
            }

            // BASIC_HEADER_DONE
            RtmpDecoderStream rd = findDecoderStream(h.getCsId());

            if (rd == null && h.getFmt() != 0) {
                throw new RtmpException("proto error, no rd");
            }

            int fmt = h.getFmt();
            boolean hasExtTime = false;

            if (fmt == 0) {
                h.setTimestamp(Util.readLongFromInput(input, true, 3));
                h.setMessageLen((int) Util.readLongFromInput(input, true, 3));
                h.setMsgTypeId((short) Util.readLongFromInput(input, true, 1));
                h.setMsgStreamId((int) Util.readLongFromInput(input, false, 4));

                if (h.getTimestamp() == 0xFFFFFF) {
                    hasExtTime = true;
                }

                if (rd == null) {
                    rd = addDecoderStream(h);
                }

                rd.rTimeDelta = 0;
                rd.rMessageLen = h.getMessageLen();
                rd.rMsgTypeId = h.getMsgTypeId();
                rd.rMsgStreamId = h.getMsgStreamId();
                rd.rTimestamp = h.getTimestamp();

                if (h.getTimestamp() != 0xFFFFFF) {
                    h.setCalTimestamp(h.getTimestamp());
                    rd.rCalTimestamp = h.getCalTimestamp();
                }
            } else if (fmt == 1) {
                h.setTimestamp(Util.readLongFromInput(input, true, 3));
                h.setMessageLen((int) Util.readLongFromInput(input, true, 3));
                h.setMsgTypeId((short) Util.readLongFromInput(input, true, 1));

                if (h.getTimestamp() == 0xFFFFFF) {
                    hasExtTime = true;
                }

                rd.rTimeDelta = h.getTimestamp();
                rd.rMessageLen = h.getMessageLen();
                rd.rMsgTypeId = h.getMsgTypeId();

                if (h.getTimestamp() != 0xFFFFFF) {
                    h.setCalTimestamp(rd.rCalTimestamp + rd.rTimeDelta);
                    rd.rCalTimestamp = h.getCalTimestamp();
                }

                h.setMsgStreamId((int) rd.rMsgStreamId);

            } else if (fmt == 2) {
                h.setTimestamp(Util.readLongFromInput(input, true, 3));

                if (h.getTimestamp() == 0xFFFFFF) {
                    hasExtTime = true;
                }

                rd.rTimeDelta = h.getTimestamp();
                h.setMessageLen((int) rd.rMessageLen);
                h.setMsgTypeId((short) rd.rMsgTypeId);

                if (!hasExtTime) {
                    h.setCalTimestamp(rd.rCalTimestamp + rd.rTimeDelta);
                    rd.rCalTimestamp = h.getCalTimestamp();
                }

                h.setMsgStreamId((int) rd.rMsgStreamId);

            } else if (fmt == 3) {
                if(rd.header.getTimestamp() == 0xFFFFFF && msg.getData() != null){
                    hasExtTime = true;
                }

                h.setMessageLen((int) rd.rMessageLen);
                h.setMsgTypeId((short) rd.rMsgTypeId);

                if(!hasExtTime && msg.getData() == null){
                    h.setCalTimestamp(rd.rCalTimestamp + rd.rTimeDelta);
                    rd.rCalTimestamp = h.getCalTimestamp();
                }

                h.setMsgStreamId((int) rd.rMsgStreamId);

            } else {
                throw new RtmpException("bad fmt");
            }

            // ext time
            if(hasExtTime){
                h.setExtendTimestamp((int) Util.readLongFromInput(input, true, 4));
                rd.rExtendTimestamp = h.getExtendTimestamp();

                if(msg.getData() == null){
                    if(h.getFmt() != 0){
                        h.setCalTimestamp(rd.rCalTimestamp + rd.rExtendTimestamp);
                        rd.rCalTimestamp = h.getCalTimestamp();

                    } else {
                        h.setCalTimestamp(rd.rExtendTimestamp);
                        rd.rCalTimestamp = h.getCalTimestamp();
                    }
                }
            }

            //
            if(msg.getData() == null){
                msg.setData(new byte[h.getMessageLen()]);
            }

            int needRead = h.getMessageLen() - bodyRead;
            if(needRead > chunkSize){
                needRead = chunkSize;
            }

            byte[] readed = Util.readFromInput(input, needRead);
            System.arraycopy(readed, 0, msg.getData(), bodyRead, needRead);

            bodyRead += needRead;

            if(msg.getHeader().getMessageLen() == bodyRead){
                break;
            }
        }

        return msg;
    }
}
