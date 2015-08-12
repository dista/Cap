package com.dista.org.cap.media;

import com.dista.org.cap.exception.AVException;

/**
 * Created by dista on 2015/8/12.
 */
public class NalUtil {
    public static int getNalType(byte[] buf) throws AVException {
        int p = 0;
        for(int i = 0; i < buf.length; i++){
            if(buf[i] != 0){
                p = i;
                break;
            }
        }

        if(p < 2 || p >= buf.length || (p + 1) >= buf.length || buf[p] != 0x01){
            throw new AVException("not nal");
        }

        return (buf[p+1] & 0x1F);
    }

    public static int findStartCode(byte[] buf, int offset){
        long t = 0xFFFFFFFF;

        for(int i = offset; i < buf.length; i++){
            t = (t << 8) + (buf[i] & 0xFF);

            if((t & 0xFFFFFF) == 0x000001){
                return i - 2;
            }
        }

        return -1;
    }
}
