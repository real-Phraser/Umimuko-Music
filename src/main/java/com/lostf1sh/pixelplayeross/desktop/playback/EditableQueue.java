package com.lostf1sh.pixelplayeross.desktop.playback;

import java.util.*;

/** Worker-confined queue with identities for repeated occurrences of a song. */
public final class EditableQueue<T> {
    public record Entry<T>(long key, T value) { }
    private final List<Entry<T>> entries=new ArrayList<>();
    private final List<Entry<T>> original=new ArrayList<>();
    private long sequence;
    private int index=-1;
    private boolean shuffled;
    public List<Entry<T>> entries(){return List.copyOf(entries);}
    public List<T> tracks(){return entries.stream().map(Entry::value).toList();}
    public int index(){return index;}
    public int size(){return entries.size();}
    public boolean shuffled(){return shuffled;}
    public T current(){return index>=0 && index<entries.size()?entries.get(index).value():null;}
    private long currentKey(){return index>=0 && index<entries.size()?entries.get(index).key():-1;}
    private int find(long key){for(int i=0;i<entries.size();i++)if(entries.get(i).key()==key)return i;return -1;}
    public void replace(List<T> values,int selected,boolean shuffle){
        entries.clear();original.clear();
        for(T value:values)entries.add(new Entry<>(++sequence,value));
        original.addAll(entries);index=entries.isEmpty()?-1:Math.max(0,Math.min(selected,entries.size()-1));
        shuffled=false;if(shuffle)toggleShuffle();
    }
    public boolean select(int target){if(target<0 || target>=entries.size())return false;index=target;return true;}
    public boolean selectKey(long key){return select(find(key));}
    public boolean step(int delta,boolean wrap){
        if(index<0 || entries.isEmpty())return false;
        int target=index+delta;
        if(wrap)target=Math.floorMod(target,entries.size());
        return select(target);
    }
    public void insertNext(T value){
        Entry<T> entry=new Entry<>(++sequence,value);
        if(entries.isEmpty()){entries.add(entry);original.add(entry);index=0;return;}
        long selected=currentKey();entries.add(index+1,entry);
        int base=0;while(base<original.size() && original.get(base).key()!=selected)base++;
        original.add(Math.min(base+1,original.size()),entry);
    }
    public void append(Collection<T> values){
        for(T value:values){Entry<T> entry=new Entry<>(++sequence,value);entries.add(entry);original.add(entry);}
        if(index<0 && !entries.isEmpty())index=0;
    }
    public void toggleShuffle(){
        long selected=currentKey();shuffled=!shuffled;
        if(shuffled){
            List<Entry<T>> rest=new ArrayList<>(entries);rest.removeIf(e->e.key()==selected);Collections.shuffle(rest);
            Entry<T> current=index>=0?entries.get(index):null;entries.clear();if(current!=null)entries.add(current);entries.addAll(rest);
        }else{entries.clear();entries.addAll(original);}
        index=find(selected);
    }
    public void moveTo(long key,int target){
        int from=find(key);if(from<0 || entries.size()<2)return;
        target=Math.max(0,Math.min(target,entries.size()-1));if(from==target)return;
        long selected=currentKey();Entry<T> entry=entries.remove(from);entries.add(target,entry);index=find(selected);
        // An explicit reorder becomes the order to restore after shuffle is disabled.
        original.clear();original.addAll(entries);
    }
    public void moveBy(long key,int delta){int from=find(key);if(from>=0)moveTo(key,from+delta);}
    public boolean remove(Set<Long> keys){
        long selected=currentKey();int oldIndex=index;
        Long next=null;
        for(int i=oldIndex+1;i<entries.size();i++)if(!keys.contains(entries.get(i).key())){next=entries.get(i).key();break;}
        entries.removeIf(e->keys.contains(e.key()));original.removeIf(e->keys.contains(e.key()));
        index=find(selected);
        if(index<0 && !entries.isEmpty())index=next!=null?find(next):entries.size()-1;
        return currentKey()!=selected;
    }
    public void clearPlayed(){
        Set<Long> keys=new HashSet<>();for(int i=0;i<index;i++)keys.add(entries.get(i).key());remove(keys);
    }
}
