package com.dista.org.cap.util;

import java.io.IOException;
import java.io.InputStream;

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
}
