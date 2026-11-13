package com.lostf1sh.pixelplayeross.desktop.playback;

import com.sun.jna.*;
import com.sun.jna.ptr.PointerByReference;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import static com.lostf1sh.pixelplayeross.desktop.playback.WinRtCom.*;

/** TypedEventHandler<SMTC, ButtonPressedEventArgs>, with honest QI and agile marshaling. */
final class SmtcButtonDelegate implements AutoCloseable {
    // Windows SDK windows.media.h, ITypedEventHandler specialization.
    static final String HANDLER_IID="0557e996-7b23-5bae-aa81-ea0d671143a4";
    static final String UNKNOWN_IID="00000000-0000-0000-c000-000000000046";
    static final String AGILE_IID="94ea2b94-e9cc-49e0-c0ff-ee64ca8f5b90";
    static final String MARSHAL_IID="00000003-0000-0000-c000-000000000046";
    static final int E_NOINTERFACE=0x80004002,E_POINTER=0x80004003,E_FAIL=0x80004005;
    private static final Map<Long,SmtcButtonDelegate> LIVE=new ConcurrentHashMap<>();
    private final Memory pointer=new Memory(Native.POINTER_SIZE);
    private final Memory vtable=new Memory(4L*Native.POINTER_SIZE);
    private final AtomicInteger references=new AtomicInteger(1);
    private final AtomicBoolean ownerReleased=new AtomicBoolean();
    private final IntConsumer listener;
    private final BooleanSupplier closed;
    private volatile Pointer marshaler;
    // Win64 has a unified calling convention. These callbacks are only shipped in x64 packages.
    private interface Query extends Callback {int invoke(Pointer self,Pointer iid,PointerByReference out);}
    private interface Ref extends Callback {int invoke(Pointer self);}
    private interface Invoke extends Callback {int invoke(Pointer self,Pointer sender,Pointer args);}
    private final Query query=this::queryInterface;
    private final Ref addRef=self->references.incrementAndGet();
    private final Ref release=self->releaseReference();
    private final Invoke invoke=this::invokeButton;
    private int invokeButton(Pointer self,Pointer sender,Pointer args) {
        if(closed.getAsBoolean())return 0;
        if(args==null)return E_POINTER;
        try {
            Memory value=new Memory(4);int hr=call(args,6,value);
            if(hr<0)return hr;
            listener.accept(value.getInt(0));return 0;
        }catch(Throwable failure){System.err.println("Umimuko media button callback: "+failure);return E_FAIL;}
    }
    SmtcButtonDelegate(IntConsumer listener,BooleanSupplier closed){this(listener,closed,true);}
    // Only tests pass false; production always aggregates Windows' free-threaded marshaler.
    SmtcButtonDelegate(IntConsumer listener,BooleanSupplier closed,boolean createMarshaler) {
        this.listener=listener;this.closed=closed;
        vtable.setPointer(0,CallbackReference.getFunctionPointer(query));
        vtable.setPointer(Native.POINTER_SIZE,CallbackReference.getFunctionPointer(addRef));
        vtable.setPointer(2L*Native.POINTER_SIZE,CallbackReference.getFunctionPointer(release));
        vtable.setPointer(3L*Native.POINTER_SIZE,CallbackReference.getFunctionPointer(invoke));
        pointer.setPointer(0,vtable);LIVE.put(Pointer.nativeValue(pointer),this);
        if(createMarshaler) {
            try {
                PointerByReference out=new PointerByReference();
                int hr=NativeLibrary.getInstance("ole32").getFunction("CoCreateFreeThreadedMarshaler",Function.ALT_CONVENTION)
                    .invokeInt(new Object[]{pointer,out});
                if(hr<0)throw new IllegalStateException("Free-threaded media marshaler: 0x"+Integer.toHexString(hr));
                marshaler=out.getValue();
            }catch(Throwable failure){close();throw failure;}
        }
    }
    Pointer pointer(){return pointer;}
    int referenceCount(){return references.get();}
    static int liveCount(){return LIVE.size();}
    private static boolean is(Pointer iid,String value){return Arrays.equals(iid.getByteArray(0,16),guid(value).getByteArray(0,16));}
    private int queryInterface(Pointer self,Pointer iid,PointerByReference out) {
        if(out==null || out.getPointer()==null)return E_POINTER;
        out.setValue(Pointer.NULL);
        if(iid==null)return E_POINTER;
        if(is(iid,UNKNOWN_IID)||is(iid,HANDLER_IID)||is(iid,AGILE_IID)) {
            references.incrementAndGet();out.setValue(pointer);return 0;
        }
        if(is(iid,MARSHAL_IID) && marshaler!=null)return call(marshaler,0,iid,out);
        // In particular, never claim INoMarshal or IInspectable. A delegate has only four slots.
        return E_NOINTERFACE;
    }
    private int releaseReference() {
        int remaining=references.decrementAndGet();
        if(remaining==0) {
            if(marshaler!=null){Pointer value=marshaler;marshaler=null;call(value,2);}
            LIVE.remove(Pointer.nativeValue(pointer));
        }
        return remaining;
    }
    @Override public void close(){if(ownerReleased.compareAndSet(false,true))releaseReference();}
}
