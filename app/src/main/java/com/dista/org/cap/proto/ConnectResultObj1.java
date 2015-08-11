package com.dista.org.cap.proto;

/**
 * Created by dista on 2015/8/11.
 */
public class ConnectResultObj1 {
    private String fmsVer;
    private int capabilities;
    private int mode;

    public String getFmsVer() {
        return fmsVer;
    }

    public void setFmsVer(String fmsVer) {
        this.fmsVer = fmsVer;
    }

    public int getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(int capabilities) {
        this.capabilities = capabilities;
    }

    public int getMode() {
        return mode;
    }

    public void setMode(int mode) {
        this.mode = mode;
    }
}
