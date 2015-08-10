package com.dista.org.cap.net;

import com.dista.org.cap.exception.RtmpException;
import com.dista.org.cap.media.AVMetaData;
import com.dista.org.cap.media.AVPacket;
import com.dista.org.cap.util.Util;

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
    }

    public void publish(AVMetaData meta){

    }

    public void sendAVPacket(AVPacket pkt){

    }
}
