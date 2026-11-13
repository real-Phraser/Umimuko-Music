package com.lostf1sh.pixelplayeross.desktop.playback;

import com.sun.jna.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

/** Small ABI helpers; the non-Windows convention also permits native callback tests. */
final class WinRtCom {
    private WinRtCom(){ }
    static Memory guid(String value) {
        UUID uuid=UUID.fromString(value);Memory buffer=new Memory(16);
        ByteBuffer b=buffer.getByteBuffer(0,16).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt((int)(uuid.getMostSignificantBits()>>>32));
        b.putShort((short)(uuid.getMostSignificantBits()>>>16));b.putShort((short)uuid.getMostSignificantBits());
        b.order(ByteOrder.BIG_ENDIAN).putLong(uuid.getLeastSignificantBits());return buffer;
    }
    static int call(Pointer object,int slot,Object... args) {
        Pointer address=object.getPointer(0).getPointer((long)slot*Native.POINTER_SIZE);
        Object[] all=new Object[args.length+1];all[0]=object;System.arraycopy(args,0,all,1,args.length);
        return Function.getFunction(address,Platform.isWindows()?Function.ALT_CONVENTION:Function.C_CONVENTION).invokeInt(all);
    }
}
