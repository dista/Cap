package com.dista.org.cap.proto;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Vector;

/**
 * Created by dista on 2015/8/10.
 */
public class RtmpEncoder {
    private class RtmpChunkStream {
        private int csId;
        private long timestamp;
        private int messageLen;
        private short msgTypeId;
        private int msgStreamId;
        private long timeDelta;
        private long writeTimestamp;

        public int getCsId() {
            return csId;
        }

        public void setCsId(int csId) {
            this.csId = csId;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }

        public int getMessageLen() {
            return messageLen;
        }

        public void setMessageLen(int messageLen) {
            this.messageLen = messageLen;
        }

        public short getMsgTypeId() {
            return msgTypeId;
        }

        public void setMsgTypeId(short msgTypeId) {
            this.msgTypeId = msgTypeId;
        }

        public int getMsgStreamId() {
            return msgStreamId;
        }

        public void setMsgStreamId(int msgStreamId) {
            this.msgStreamId = msgStreamId;
        }

        public long getTimeDelta() {
            return timeDelta;
        }

        public void setTimeDelta(long timeDelta) {
            this.timeDelta = timeDelta;
        }

        public long getWriteTimestamp() {
            return writeTimestamp;
        }

        public void setWriteTimestamp(long writeTimestamp) {
            this.writeTimestamp = writeTimestamp;
        }
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    private int chunkSize;
    private Vector<RtmpChunkStream> chunkStreams;

    public RtmpEncoder(int chunkSize){
        this.chunkSize = chunkSize;
        this.chunkStreams = new Vector<RtmpChunkStream>();
    }

    private RtmpChunkStream findChunkStream(RtmpMsg msg){
        int csId = msg.getHeader().getCsId();

        for (RtmpChunkStream cs : chunkStreams
             ) {
            if(cs.getCsId() == csId){
                return cs;
            }
        }

        return null;
    }

    private RtmpChunkStream addChunkStream(RtmpMsg msg){
        RtmpChunkStream cs = new RtmpChunkStream();
        cs.setCsId(msg.getHeader().getCsId());

        chunkStreams.add(cs);
        return null;
    }

    private void writeHeader(ByteBuffer bf, RtmpChunkStream cs, RtmpMsg msg,
                             int chunkType, boolean isFirstChunk){
        // TODO： implement it
    }

    private byte[] encodeInternal(RtmpMsg msg, int ct){
        int bufLen = msg.getData().length / chunkSize;
        if(bufLen % chunkSize > 0){
            bufLen += 1;
        }
        bufLen *= (3 + 11 + 4 + chunkSize);
        ByteBuffer bf = ByteBuffer.allocate(bufLen);

        RtmpChunkStream cs = findChunkStream(msg);
        int chunkType = -1;
        if(cs == null){
            cs = addChunkStream(msg);
            chunkType = 0;
        }

        if(chunkType == -1){
            if(ct == -1){
                if(cs.getMsgStreamId() != msg.getHeader().getMsgStreamId()){
                    chunkType = 0;
                } else {
                    if(cs.getMsgTypeId() != msg.getHeader().getMsgTypeId() ||
                            cs.getMessageLen() != msg.getHeader().getMessageLen()){
                        chunkType = 1;
                    } else {
                        if(cs.getTimestamp() != msg.getHeader().getTimestamp()){
                            chunkType = 2;
                        } else {
                            chunkType = 3;
                        }
                    }
                }
            } else {
                chunkType = ct;
            }
        }

        int dataLen = msg.getData().length;
        boolean isFirstChunk = true;
        for(int offset = 0; offset < dataLen;){
            int left = dataLen - offset;
            int writeSize = left;
            if(left > chunkSize){
                writeSize = chunkSize;
            }

            if(isFirstChunk){
                writeHeader(bf, cs, msg, chunkType, isFirstChunk);
            } else {
                writeHeader(bf, cs, msg, 3, isFirstChunk);
            }

            isFirstChunk = false;

            bf.put(msg.getData(), offset, writeSize);
            offset += writeSize;
        }

        long timeDelta = 0;
        if(chunkType == 1 || chunkType == 2){
            timeDelta = msg.getHeader().getCalTimestamp() - cs.getTimestamp();
        }

        // set cs
        cs.setCsId(msg.getHeader().getCsId());
        cs.setTimestamp(msg.getHeader().getCalTimestamp());
        cs.setMessageLen(msg.getData().length);
        cs.setMsgTypeId(msg.getHeader().getMsgTypeId());
        cs.setMsgStreamId(msg.getHeader().getMsgStreamId());
        cs.setTimeDelta(timeDelta);

        int bfLen = bf.position();
        bf.position(0);

        byte[] ret = new byte[bfLen];
        bf.get(ret);

        return ret;
    }

    public byte[] encode(RtmpMsg msg){
        return encodeInternal(msg, -1);
    }
}
