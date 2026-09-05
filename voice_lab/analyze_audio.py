#!/usr/bin/env python3
import argparse, json, math, subprocess, tempfile, wave
from pathlib import Path
import numpy as np


def contiguous(mask):
    out=[]; start=None
    for i,v in enumerate(mask):
        if v and start is None: start=i
        elif not v and start is not None:
            out.append((start,i)); start=None
    if start is not None: out.append((start,len(mask)))
    return out


def load_pcm16(path:Path):
    """Decode any WAV produced by the candidate (including IEEE float WAV) to PCM16."""
    tmp=None
    try:
        try:
            w=wave.open(str(path),'rb')
        except wave.Error:
            tmp=tempfile.NamedTemporaryFile(suffix='.wav',delete=False)
            tmp.close()
            subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-i',str(path),'-acodec','pcm_s16le',tmp.name],check=True)
            w=wave.open(tmp.name,'rb')
        with w:
            rate=w.getframerate(); channels=w.getnchannels(); width=w.getsampwidth(); n=w.getnframes(); raw=w.readframes(n)
        if width != 2:
            raise RuntimeError(f"{path}: PCM conversion produced sample width={width}")
        x=np.frombuffer(raw,dtype='<i2').astype(np.float32)/32768.0
        if channels>1: x=x.reshape(-1,channels).mean(axis=1)
        return rate,x
    finally:
        if tmp:
            Path(tmp.name).unlink(missing_ok=True)


def analyze(path:Path):
    rate,x=load_pcm16(path)
    duration=len(x)/rate if rate else 0.0
    if len(x)==0: return {"file":str(path),"duration_s":0,"error":"empty"}
    peak=float(np.max(np.abs(x)))
    rms=float(np.sqrt(np.mean(x*x)+1e-12))
    rms_db=20*math.log10(max(rms,1e-9))
    clipping=float(np.mean(np.abs(x)>=0.995))

    frame=max(1,int(rate*0.02)); hop=frame
    vals=[]
    for i in range(0,max(1,len(x)-frame+1),hop):
        f=x[i:i+frame]
        vals.append(float(np.sqrt(np.mean(f*f)+1e-12)))
    vals=np.asarray(vals)
    p70=float(np.percentile(vals,70)) if len(vals) else 0
    threshold=max(0.006,min(0.025,p70*0.22))
    silent=vals<threshold
    segments=[]
    for a,b in contiguous(silent):
        dur=(b-a)*0.02
        if a>1 and b<len(silent)-2 and dur>=0.18:
            segments.append({"start_s":round(a*0.02,3),"duration_s":round(dur,3)})
    max_gap=max([s["duration_s"] for s in segments],default=0)
    long_gaps=[s for s in segments if s["duration_s"]>=0.65]

    sample=x[:min(len(x),rate*12)]
    if len(sample)>512:
        spec=np.abs(np.fft.rfft(sample*np.hanning(len(sample))))
        freqs=np.fft.rfftfreq(len(sample),1/rate)
        centroid=float((spec*freqs).sum()/max(spec.sum(),1e-9))
    else: centroid=0.0

    score=100
    score-=min(45,len(long_gaps)*18)
    if max_gap>1.2: score-=18
    if clipping>0.001: score-=15
    if rms_db<-30: score-=10
    if rms_db>-10: score-=8
    score=max(0,score)
    suggestions=[]
    if long_gaps: suggestions.append("internal_silence: reduce chunking / prebuffer audio / avoid synthetic pauses")
    if max_gap>1.2: suggestions.append("severe_gap: reject candidate for realtime dialogue")
    if clipping>0.001: suggestions.append("clipping: lower output gain")
    if rms_db<-30: suggestions.append("quiet: normalize or increase gain")
    if rms_db>-10: suggestions.append("hot_level: reduce gain")
    return {
        "file":str(path),"sample_rate":rate,"duration_s":round(duration,3),"peak":round(peak,5),
        "rms_dbfs":round(rms_db,2),"clipping_ratio":round(clipping,6),
        "spectral_centroid_hz":round(centroid,1),"internal_silences":segments,
        "max_internal_gap_s":round(max_gap,3),"long_internal_gaps":len(long_gaps),
        "objective_score":score,"suggestions":suggestions
    }


def main():
    ap=argparse.ArgumentParser(); ap.add_argument('files',nargs='+'); ap.add_argument('--out',default='voice_lab_report.json')
    args=ap.parse_args()
    rows=[analyze(Path(p)) for p in args.files]
    summary={
        "files":rows,
        "mean_score":round(sum(r.get('objective_score',0) for r in rows)/max(1,len(rows)),2),
        "max_gap_s":max([r.get('max_internal_gap_s',0) for r in rows],default=0),
        "candidate_ok_for_manual_listen":all(r.get('long_internal_gaps',1)==0 and r.get('clipping_ratio',1)<0.001 for r in rows)
    }
    Path(args.out).write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(summary,ensure_ascii=False,indent=2))

if __name__=='__main__': main()
