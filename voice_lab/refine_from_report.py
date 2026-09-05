#!/usr/bin/env python3
import argparse, json
from pathlib import Path

ap=argparse.ArgumentParser(); ap.add_argument('report'); ap.add_argument('--out',default='refinement.txt'); args=ap.parse_args()
r=json.loads(Path(args.report).read_text(encoding='utf-8'))
notes=[]
if r.get('max_gap_s',0)>=0.65:
    notes.append('Keep every sentence connected. Do not insert pauses except at explicit punctuation; internal pauses should stay under 350 milliseconds.')
levels=[x.get('rms_dbfs',-99) for x in r.get('files',[])]
if levels and sum(levels)/len(levels)<-24:
    notes.append('Use a slightly more present, supported voice while staying natural and close-mic.')
if any(x.get('clipping_ratio',0)>0.001 for x in r.get('files',[])):
    notes.append('Reduce vocal intensity slightly; avoid hard peaks and shouting.')
# Always push away from announcer/robotic cadence on pass two.
notes.append('Prioritize conversational Russian phrasing, semantic emphasis and continuous breath groups; avoid evenly timed robotic word-by-word cadence.')
text=' '.join(notes)
Path(args.out).write_text(text,encoding='utf-8')
print(text)
