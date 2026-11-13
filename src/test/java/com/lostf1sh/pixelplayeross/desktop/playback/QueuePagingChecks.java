package com.lostf1sh.pixelplayeross.desktop.playback;

public final class QueuePagingChecks {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){runChecks();System.out.println("PASS: queue 30/50-page boundaries, no skipped/duplicated indices, empty/last pages, shrink clamp, index jump and integer overflow protection");}
    public static void runChecks(){
        for(int size:new int[]{30,50}){
            for(int total:new int[]{0,1,29,30,31,49,50,51,84,120,1001}){
                int previous=0;
                for(int page=0;page<QueuePaging.pageCount(total,size);page++){
                    int start=QueuePaging.start(total,size,page),end=QueuePaging.end(total,size,page);
                    check(start==previous,"gap/duplicate");check(end-start<=size,"page limit");
                    check(end>=start && end<=total,"bounds");previous=end;
                    if(end>start) check(QueuePaging.pageForIndex(total,size,end-1)==page,"track page");
                }
                check(previous==total,"full queue remains covered");
                check(QueuePaging.start(total,size,-100)==0,"negative page");
                check(QueuePaging.end(total,size,Integer.MAX_VALUE)==total,"clamp after shrink");
            }
            int total=Integer.MAX_VALUE;
            check(QueuePaging.end(total,size,QueuePaging.pageCount(total,size)-1)==total,"overflow");
        }
        boolean failed=false;try{QueuePaging.pageCount(1,0);}catch(IllegalArgumentException e){failed=true;}
        check(failed,"reject zero page size");
    }
}
