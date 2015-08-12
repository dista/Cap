package com.dista.org.cap.net;

import android.util.Log;

import com.dista.org.cap.exception.RtmpException;
import com.dista.org.cap.media.AVMetaData;
import com.dista.org.cap.media.AVPacket;
import com.dista.org.cap.proto.Amf;
import com.dista.org.cap.proto.ConnectObj;
import com.dista.org.cap.proto.ConnectResultObj1;
import com.dista.org.cap.proto.ConnectResultObj2;
import com.dista.org.cap.proto.RtmpDecoder;
import com.dista.org.cap.proto.RtmpEncoder;
import com.dista.org.cap.proto.RtmpMsg;
import com.dista.org.cap.util.Util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by dista on 2015/8/7.
 */
public class RtmpClient {
    private Socket sock;
    private RtmpHandshake handshake;
    private String path;
    private RtmpEncoder encoder;
    private RtmpDecoder decoder;
    private int sequenceNumber;
    private Map<Integer, String> sequence;
    private int streamId;
    private int avSequenceNumber;
    private Map<Integer, String> avSequence;

    public RtmpClient(){
        sock = new Socket();
        sequenceNumber = 1;
        sequence = new HashMap<Integer, String>();
        avSequenceNumber = 0;
        avSequence = new HashMap<Integer, String>();
    }

    public void close(){
        try {
            this.sock.close();
        } catch (IOException e) {
        }
    }

    public void connect(SocketAddress remoteAddr, int timeout,
                        String path) throws RtmpException {
        try {
            tcpConnect(remoteAddr, timeout);
        } catch (IOException e) {
            throw new RtmpException("tcpConnect", e);
        }

        try {
            handshake();
        } catch (IOException e) {
            throw new RtmpException("handShake", e);
        }

        this.path = path;
    }

    private void tcpConnect(SocketAddress remoteAddr, int timeout) throws IOException {
        sock.connect(remoteAddr, timeout);
    }

    private void handshake() throws RtmpException, IOException {
        handshake = new RtmpHandshake();

        byte[] h1 = handshake.genHandshake1();
        sock.getOutputStream().write(h1);

        byte[] resp = Util.readFromInput(sock.getInputStream(), 1537);

        byte[] h2 = handshake.genHandshake2(resp);
        sock.getOutputStream().write(h2);

        Util.readFromInput(sock.getInputStream(), 1536);

        encoder = new RtmpEncoder();
        decoder = new RtmpDecoder();
        decoder.setInput(sock.getInputStream());
    }

    private boolean handleMsg(RtmpMsg msg){
        int typeId = msg.getHeader().getMsgTypeId();

        boolean isHandled = false;

        // SetChunkSize;
        if(typeId == 0x01){
            int newChunkSize = ByteBuffer.wrap(msg.getData()).order(ByteOrder.BIG_ENDIAN).getInt();

            Log.d("", "new chunkSize: " + newChunkSize);
            decoder.setChunkSize(newChunkSize);

            isHandled = true;
        } else if(typeId == 0x14){
        } else if(typeId == 0x4){
            isHandled = true;
        } else if(typeId == 0x5){
            isHandled = true;
        } else if(typeId == 0x6){
            isHandled = true;
        }

        return isHandled;
    }

    public void publish(AVMetaData meta) throws RtmpException, NoSuchFieldException, InstantiationException {
        try {
            sendConnect();
            handleResult();

            sendWindowAckSize(2500000);
            sendReleaseStream();
            sendFCPublish();
            sendCreateStream();
            handleResult();

            sendPublish();
            handleResult();

            sendSetChunkSize(2046);
            encoder.setChunkSize(2046);

            sendSetDataFrame(meta);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RtmpException("publish", e);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            throw new RtmpException("publish", e);
        }
    }

    private void sendSetDataFrame(AVMetaData meta){

    }

    private void handleResult() throws IOException, RtmpException, InstantiationException, IllegalAccessException {
        while(true) {
            RtmpMsg msg = decoder.decode();

            boolean isHandled = handleMsg(msg);

            if(!isHandled){
                int typeId = msg.getHeader().getMsgTypeId();
                ByteBuffer bf = ByteBuffer.wrap(msg.getData());

                if(typeId == 0x14){
                    Map<Integer, String> theSeq = sequence;
                    if(msg.getHeader().getMsgStreamId() != 0){
                        theSeq = avSequence;
                    }

                    String cmd = Amf.readString(bf);

                    // TODO: handle this
                    if(cmd.equals("_result")){
                        int seq = (int) Amf.readNumber(bf);

                        String sendCmd = theSeq.get(seq);

                        if(sendCmd != null) {
                            if(sendCmd.equals("connect")) {
                                ConnectResultObj1 r1 = (ConnectResultObj1) Amf.readObject(bf, ConnectResultObj1.class);
                                ConnectResultObj2 r2 = (ConnectResultObj2) Amf.readObject(bf, ConnectResultObj2.class);

                                break;
                            } else if(sendCmd.equals("createStream")){
                                Amf.readObject(bf, Object.class);

                                streamId = (int) Amf.readNumber(bf);
                                break;
                            }
                        }
                    } else if(cmd.equals("_error")){
                        throw new RtmpException("proto error. publish: " + cmd);
                    } else if(cmd.equals("onStatus")){
                        int seq = (int) Amf.readNumber(bf);

                        String sendCmd = theSeq.get(seq);
                        if(sendCmd != null) {
                            if(sendCmd.equals("publish")){
                                // TODO: test if publish OK
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    private void sendWindowAckSize(int size) throws IOException {
        RtmpMsg msg = new RtmpMsg();
        msg.getHeader().setCsId(2);
        msg.getHeader().setMsgTypeId((short) 0x05);

        msg.setData(Util.IntToBytes(true, size, 4));

        sendMsg(msg);
    }

    private void sendSetChunkSize(int chunkSize) throws IOException {
        RtmpMsg msg = new RtmpMsg();
        msg.getHeader().setCsId(2);
        msg.getHeader().setMsgTypeId((short) 0x01);

        msg.setData(Util.IntToBytes(true, chunkSize, 4));

        sendMsg(msg);
    }

    private void sendConnect() throws IOException, IllegalAccessException {
        RtmpMsg msg = new RtmpMsg();
        msg.getHeader().setCsId(3);
        msg.getHeader().setMsgTypeId((short) 0x14);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        String cmd = "connect";
        int sq = sequenceNumber++;
        Amf.write(os, cmd);
        Amf.write(os, sq);

        ConnectObj obj = new ConnectObj();

        String[] items = this.path.split("/");

        obj.app = items[0];
        obj.tcUrl = "";
        obj.swfUrl = "";
        obj.type = "";
        obj.flashVer = "Cap/1.0";

        Amf.write(os, obj);

        final byte[] bytes = os.toByteArray();

        msg.setData(bytes);

        sendMsg(msg);

        sequence.put(sq, cmd);
    }

    private void sendReleaseStream() throws IOException, IllegalAccessException {
        RtmpMsg msg = new RtmpMsg();
        msg.getHeader().setCsId(3);
        msg.getHeader().setMsgTypeId((short) 0x14);

        String[] items = this.path.split("/");

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        Amf.write(os, "releaseStream");
        Amf.write(os, sequenceNumber++);
        Amf.write(os, null);
        Amf.write(os, items[1]);

        final byte[] bytes = os.toByteArray();

        msg.setData(bytes);

        sendMsg(msg);
    }

    private void sendCreateStream() throws IOException, IllegalAccessException {
        RtmpMsg msg = new RtmpMsg();
        msg.getHeader().setCsId(3);
        msg.getHeader().setMsgTypeId((short) 0x14);

        String[] items = this.path.split("/");

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        String cmd = "createStream";
        int sq = sequenceNumber++;
        Amf.write(os, cmd);
        Amf.write(os, sq);
        Amf.write(os, null);

        final byte[] bytes = os.toByteArray();

        msg.setData(bytes);

        sendMsg(msg);

        sequence.put(sq, cmd);
    }

    private void sendPublish() throws IOException, IllegalAccessException {
        RtmpMsg msg = new RtmpMsg();
        msg.getHeader().setCsId(4);
        msg.getHeader().setMsgTypeId((short) 0x14);
        msg.getHeader().setMsgStreamId(streamId);

        String[] items = this.path.split("/");

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        String cmd = "publish";
        int sq = avSequenceNumber++;
        Amf.write(os, cmd);
        Amf.write(os, sq);
        Amf.write(os, null);
        Amf.write(os, items[1]);
        Amf.write(os, items[0]);

        final byte[] bytes = os.toByteArray();

        msg.setData(bytes);

        sendMsg(msg);

        avSequence.put(sq, cmd);
    }

    private void sendFCPublish() throws IOException, IllegalAccessException {
        RtmpMsg msg = new RtmpMsg();
        msg.getHeader().setCsId(3);
        msg.getHeader().setMsgTypeId((short) 0x14);

        String[] items = this.path.split("/");

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        Amf.write(os, "FCPublish");
        Amf.write(os, sequenceNumber++);
        Amf.write(os, null);
        Amf.write(os, items[1]);

        final byte[] bytes = os.toByteArray();

        msg.setData(bytes);

        sendMsg(msg);
    }

    private void sendMsg(RtmpMsg msg) throws IOException {
        msg.getHeader().setMessageLen(msg.getData().length);

        byte[] toSend = encoder.encode(msg);

        sock.getOutputStream().write(toSend);
    }

    public void sendAVPacket(AVPacket pkt){
    }
}
