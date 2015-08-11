package com.dista.org.cap.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * Created by dista on 2015/8/10.
 */
public class Util {
    public static byte[] readFromInput(InputStream input, int size) throws IOException {
        byte[] ret = new byte[size];
        int offset = 0;

        while((ret.length - offset) > 0) {
            int n = input.read(ret, offset, ret.length - offset);

            if(n == -1){
                throw new IOException("no enough data");
            }

            offset += n;
        }

        return ret;
    }

    public static long readLongFromInput(InputStream input, boolean isBig, int size) throws IOException {
        byte[] buf = readFromInput(input, size);
        long ret = 0;

        for(int i = 0; i < size; i++){
            if(isBig){
                ret = (ret << 8) + (long)(buf[i] & 0xFF);
            } else {
                ret = ret + (((long)(buf[i] & 0xFF)) << (i * 8));
            }
        }

        return ret;
    }

    public static byte[] IntToBytes(boolean isBig, long v, int bytesNum){
        byte[] tmp = new byte[bytesNum];
        for(int i = 0; i < bytesNum; i++){
            byte p;
            if(isBig){
                p = (byte)(v >> (8 * ((bytesNum - 1 - i))));
            } else{
                p = (byte)(v >> (8 * i));
            }

            tmp[i] = p;
        }

        return tmp;
    }

    public static void OutStreamWriteInt(OutputStream os, boolean isBig, long v, int bytesNum) throws IOException {
        os.write(IntToBytes(isBig, v, bytesNum));
    }

    public static void ByteBufferWriteInt(ByteBuffer bf, boolean isBig, long v, int bytesNum){
        bf.put(IntToBytes(isBig, v, bytesNum));
    }
}
