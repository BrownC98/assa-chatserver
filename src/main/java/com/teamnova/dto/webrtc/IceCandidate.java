package com.teamnova.dto.webrtc;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class IceCandidate {
    public final String sdpMid;
    public final int sdpMLineIndex;
    public final String sdp;
    public final String serverUrl;
    public final AdapterType adapterType;

    public IceCandidate(String sdpMid, int sdpMLineIndex, String sdp) {
        this.sdpMid = sdpMid;
        this.sdpMLineIndex = sdpMLineIndex;
        this.sdp = sdp;
        this.serverUrl = "";
        this.adapterType = AdapterType.UNKNOWN;
    }

    IceCandidate(String sdpMid, int sdpMLineIndex, String sdp, String serverUrl,
            AdapterType adapterType) {
        this.sdpMid = sdpMid;
        this.sdpMLineIndex = sdpMLineIndex;
        this.sdp = sdp;
        this.serverUrl = serverUrl;
        this.adapterType = adapterType;
    }

    public String toString() {
        String var10000 = this.sdpMid;
        return var10000 + ":" + this.sdpMLineIndex + ":" + this.sdp + ":" + this.serverUrl + ":"
                + this.adapterType.toString();
    }

    String getSdpMid() {
        return this.sdpMid;
    }

    String getSdp() {
        return this.sdp;
    }

    public boolean equals(Object object) {
        if (!(object instanceof IceCandidate that)) {
            return false;
        } else {
            return objectEquals(this.sdpMid, that.sdpMid) && this.sdpMLineIndex == that.sdpMLineIndex
                    && objectEquals(this.sdp, that.sdp);
        }
    }

    public int hashCode() {
        Object[] values = new Object[] { this.sdpMid, this.sdpMLineIndex, this.sdp };
        return Arrays.hashCode(values);
    }

    private static boolean objectEquals(Object o1, Object o2) {
        if (o1 == null) {
            return o2 == null;
        } else {
            return o1.equals(o2);
        }
    }

    public static enum AdapterType {
        UNKNOWN(0),
        ETHERNET(1),
        WIFI(2),
        CELLULAR(4),
        VPN(8),
        LOOPBACK(16),
        ADAPTER_TYPE_ANY(32),
        CELLULAR_2G(64),
        CELLULAR_3G(128),
        CELLULAR_4G(256),
        CELLULAR_5G(512);

        public final Integer bitMask;
        private static final Map<Integer, AdapterType> BY_BITMASK = new HashMap<>();

        private AdapterType(Integer bitMask) {
            this.bitMask = bitMask;
        }

        static AdapterType fromNativeIndex(int nativeIndex) {
            return (AdapterType) BY_BITMASK.get(nativeIndex);
        }

        static {
            AdapterType[] var0 = values();
            int var1 = var0.length;

            for (int var2 = 0; var2 < var1; ++var2) {
                AdapterType t = var0[var2];
                BY_BITMASK.put(t.bitMask, t);
            }

        }
    }
}