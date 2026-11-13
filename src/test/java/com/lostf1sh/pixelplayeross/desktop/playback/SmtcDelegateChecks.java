package com.lostf1sh.pixelplayeross.desktop.playback;

import com.sun.jna.*;
import com.sun.jna.ptr.PointerByReference;
import java.lang.ref.Reference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static com.lostf1sh.pixelplayeross.desktop.playback.WinRtCom.*;

/** Exercises actual JNA native vtables without requiring a Windows media session. */
public final class SmtcDelegateChecks {
    public interface ReadButton extends Callback {int invoke(Pointer self,Pointer value);}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args){run();}
    public static void run() {
        int baseline=SmtcButtonDelegate.liveCount();AtomicInteger received=new AtomicInteger(-1);
        AtomicBoolean closed=new AtomicBoolean();
        SmtcButtonDelegate delegate=new SmtcButtonDelegate(received::set,closed::get,false);
        try {
            for(String iid:new String[]{SmtcButtonDelegate.UNKNOWN_IID,SmtcButtonDelegate.HANDLER_IID,SmtcButtonDelegate.AGILE_IID}) {
                PointerByReference out=new PointerByReference();
                check(call(delegate.pointer(),0,guid(iid),out)==0,"Supported QueryInterface failed");
                check(Pointer.nativeValue(out.getValue())==Pointer.nativeValue(delegate.pointer()),"IUnknown identity changed");
                check(delegate.referenceCount()==2,"QueryInterface did not retain the callback");
                check(call(out.getValue(),2)==1,"Release did not restore owner reference");
            }
            for(String iid:new String[]{
                "ecc8691b-c1db-4dc0-855e-65f6c551af49", // INoMarshal must NOT be claimed
                "af86e2e0-b12d-4c6a-9c5a-d7aa65101e90", // IInspectable is not a delegate interface
                SmtcButtonDelegate.MARSHAL_IID, // No native FTM in this test constructor
                "12345678-1111-2222-3333-444455556666"}) {
                PointerByReference out=new PointerByReference(new Pointer(123));
                check(call(delegate.pointer(),0,guid(iid),out)==SmtcButtonDelegate.E_NOINTERFACE,"Unsupported interface was claimed: "+iid);
                check(out.getValue()==null,"Unsupported QI did not clear the output pointer");
                check(delegate.referenceCount()==1,"Failed QI leaked a reference");
            }
            Memory argVtable=new Memory(7L*Native.POINTER_SIZE);argVtable.clear();
            Memory eventArgs=new Memory(Native.POINTER_SIZE);eventArgs.setPointer(0,argVtable);
            AtomicInteger button=new AtomicInteger();
            ReadButton getter=(self,value)->{value.setInt(0,button.get());return 0;};
            argVtable.setPointer(6L*Native.POINTER_SIZE,CallbackReference.getFunctionPointer(getter));
            for(int code:new int[]{0,1,6,7}) {
                button.set(code);received.set(-1);
                check(call(delegate.pointer(),3,Pointer.NULL,eventArgs)==0,"Invoke failed");
                check(received.get()==code,"Button code was not delivered: "+code);
            }
            closed.set(true);received.set(-1);
            check(call(delegate.pointer(),3,Pointer.NULL,eventArgs)==0 && received.get()==-1,"Callback fired after session close");
            Reference.reachabilityFence(getter);Reference.reachabilityFence(argVtable);Reference.reachabilityFence(eventArgs);
            check(call(delegate.pointer(),1)==2,"AddRef failed");
            delegate.close();check(delegate.referenceCount()==1,"Owner close ignored a native reference");
            check(SmtcButtonDelegate.liveCount()==baseline+1,"Live native callback lost its strong reference");
            check(call(delegate.pointer(),2)==0,"Final native release failed");
            check(SmtcButtonDelegate.liveCount()==baseline,"Callback retention leaked");
        } finally {delegate.close();}
        System.out.println("PASS: SMTC callback QI, INoMarshal rejection, native vtable dispatch and reference lifetime.");
    }
}
