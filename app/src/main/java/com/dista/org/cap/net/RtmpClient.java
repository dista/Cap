package com.dista.org.cap.net;

import com.dista.org.cap.exception.RtmpException;
import com.dista.org.cap.media.AVMetaData;
import com.dista.org.cap.media.AVPacket;
import com.dista.org.cap.proto.Amf;
import com.dista.org.cap.proto.ConnectObj;
import com.dista.org.cap.proto.RtmpDecoder;
import com.dista.org.cap.proto.RtmpEncoder;
import com.dista.org.cap.proto.RtmpMsg;
import com.dista.org.cap.util.Util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;

/**
 * Created by dista on 2015/8/7.
 */
public class RtmpClient {
    private Socket sock;
    private RtmpHandshake handshake;
    private String path;
    private RtmpEncoder encoder;
    private RtmpDecoder decoder;

    public RtmpClient(){
        sock = new Socket();
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
    }

    public void publish(AVMetaData meta) throws RtmpException {
        try {
            sendConnect();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RtmpException("publish", e);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            throw new RtmpException("publish", e);
        }
    }

    private void sendConnect() throws IOException, IllegalAccessException {
        RtmpMsg msg = new RtmpMsg();
        msg.getHeader().setCsId(3);
        msg.getHeader().setMsgTypeId((short) 0x14);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        Amf.write(os, "connect");
        Amf.write(os, 1);

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
    }

    private void sendMsg(RtmpMsg msg) throws IOException {
        msg.getHeader().setMessageLen(msg.getData().length);

        byte[] toSend = encoder.encode(msg);

        sock.getOutputStream().write(toSend);
    }

    public void sendAVPacket(AVPacket pkt){

    }
}
