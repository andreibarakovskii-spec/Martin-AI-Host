#!/usr/bin/env python3
import argparse, json, os, subprocess, time
from pathlib import Path


def run(cmd, log_path):
    started=time.time()
    p=subprocess.run(cmd,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
    elapsed=time.time()-started
    Path(log_path).write_text(p.stdout,encoding='utf-8')
    if p.returncode!=0:
        raise RuntimeError(f"command failed ({p.returncode}): {' '.join(cmd)}\n{p.stdout[-4000:]}")
    return elapsed


def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--cli',required=True); ap.add_argument('--model',required=True); ap.add_argument('--prompt-speech',required=True)
    ap.add_argument('--prompts',default='voice_lab/prompts.json'); ap.add_argument('--out-dir',default='voice_lab_out')
    ap.add_argument('--pass-name',default='baseline'); ap.add_argument('--threads',default='0'); ap.add_argument('--seed',default='20260905')
    ap.add_argument('--refinement',default='')
    args=ap.parse_args()
    out=Path(args.out_dir)/args.pass_name; out.mkdir(parents=True,exist_ok=True)
    prompts=json.loads(Path(args.prompts).read_text(encoding='utf-8'))
    manifest=[]
    for item in prompts:
        wav=out/f"{item['id']}.wav"; log=out/f"{item['id']}.engine.log"
        instruction=item['instruction']
        if args.refinement: instruction += ' ' + args.refinement
        cmd=[args.cli,'--model',args.model,'--prompt-speech',args.prompt_speech,'--mode','instruct',
             '--text',item['text'],'--instruction',instruction,'--output',str(wav),'--seed',args.seed,
             '--threads',args.threads,'--cpu','--verbose']
        elapsed=run(cmd,log)
        manifest.append({"id":item['id'],"text":item['text'],"instruction":instruction,
                         "wav":str(wav),"engine_log":str(log),"wall_time_s":round(elapsed,3)})
    (out/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(manifest,ensure_ascii=False,indent=2))

if __name__=='__main__': main()
