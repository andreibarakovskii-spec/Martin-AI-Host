package com.imagine.martinhost;
import java.util.*;

/** Transient, local clothing/body signature. It is deliberately not persisted. */
final class GuestAppearanceMemory {
 private static final class Entry{String name;float[] value;int samples;Entry(String n,float[] v){name=n;value=v.clone();samples=1;}}
 private final List<Entry> entries=new ArrayList<>();
 synchronized void remember(String name,float[] value){if(name==null||name.isBlank()||value==null)return;for(Entry e:entries)if(e.name.equalsIgnoreCase(name)){for(int i=0;i<e.value.length;i++)e.value[i]=(e.value[i]*Math.min(e.samples,12)+value[i])/(Math.min(e.samples,12)+1);e.samples++;return;}entries.add(new Entry(name,value));}
 synchronized String match(float[] value){if(value==null||entries.isEmpty())return "";Entry best=null;float first=Float.MAX_VALUE,second=Float.MAX_VALUE;for(Entry e:entries){float d=distance(e.value,value);if(d<first){second=first;first=d;best=e;}else if(d<second)second=d;}return best!=null&&first<.22f&&(second==Float.MAX_VALUE||second-first>.045f)?best.name:"";}
 static float distance(float[] a,float[] b){if(a==null||b==null||a.length<6||b.length<6)return Float.MAX_VALUE;return .30f*Math.abs(a[0]-b[0])+.70f*Math.abs(a[1]-b[1])+.70f*Math.abs(a[2]-b[2])+.40f*Math.abs(a[3]-b[3])+.40f*Math.abs(a[4]-b[4])+.15f*Math.abs(a[5]-b[5]);}
 synchronized void clear(){entries.clear();}
}
