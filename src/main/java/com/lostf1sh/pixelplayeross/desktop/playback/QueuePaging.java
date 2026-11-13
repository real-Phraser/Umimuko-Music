package com.lostf1sh.pixelplayeross.desktop.playback;

/** Page bounds are independent of the audio queue; rendering a page never truncates playback. */
public final class QueuePaging {
    public static int pageCount(int total, int size) {
        if(total<0 || size<=0)throw new IllegalArgumentException("Invalid paging arguments");
        return (int)Math.max(1,((long)total+size-1)/size);
    }
    public static int clamp(int total,int size,int page) {return Math.max(0,Math.min(pageCount(total,size)-1,page));}
    public static int start(int total,int size,int page) {return (int)Math.min(total,(long)clamp(total,size,page)*size);}
    public static int end(int total,int size,int page) {return (int)Math.min(total,(long)start(total,size,page)+size);}
    public static int pageForIndex(int total,int size,int index) {pageCount(total,size);return clamp(total,size,Math.max(0,index)/size);}
}
