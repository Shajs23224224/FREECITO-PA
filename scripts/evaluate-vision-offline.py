#!/usr/bin/env python3
"""Evaluate recorded generic object detections against independently annotated images.
No capture, automation, game integration, or network. Coordinates normalized xyxy.
Input JSON: {"images":[{"id":"...","truth":[{"label":"...","box":[...] }],
"predictions":[{"label":"...","box":[...],"score":0.8}]}]}.
"""
import argparse
import json
import math
from pathlib import Path


def validate(item, prediction=False):
    label, box = item['label'], item['box']
    if not isinstance(label, str) or not label.strip():
        raise ValueError('empty label')
    if len(box) != 4 or not all(isinstance(v, (float, int)) and math.isfinite(v) and 0 <= v <= 1 for v in box):
        raise ValueError('invalid normalized box')
    if box[0] >= box[2] or box[1] >= box[3]:
        raise ValueError('degenerate box')
    if prediction and not (isinstance(item['score'], (int,float)) and math.isfinite(item['score']) and 0 <= item['score'] <= 1):
        raise ValueError('invalid score')


def iou(a, b):
    inter = max(0, min(a[2], b[2])-max(a[0], b[0])) * max(0, min(a[3], b[3])-max(a[1], b[1]))
    return inter / ((a[2]-a[0])*(a[3]-a[1])+(b[2]-b[0])*(b[3]-b[1])-inter)


def evaluate(dataset, threshold=.5, score=.35):
    if not (0 < threshold <= 1 and 0 <= score <= 1):
        raise ValueError('invalid evaluation thresholds')
    images = dataset['images']
    if not images:
        raise ValueError('empty dataset')
    seen=set(); counts={}
    def add(label, kind):
        counts.setdefault(label, dict(tp=0, fp=0, fn=0))[kind]+=1
    for image in images:
        identifier=image['id']
        if not isinstance(identifier, str) or not identifier or identifier in seen:
            raise ValueError('invalid or duplicate image id')
        seen.add(identifier)
        truth=image['truth']; preds=image['predictions']
        for t in truth: validate(t)
        for p in preds: validate(p, True)
        unmatched=set(range(len(truth)))
        for p in sorted((p for p in preds if p['score']>=score), key=lambda p:-p['score']):
            choices=[(iou(p['box'], truth[i]['box']), -i) for i in unmatched if truth[i]['label']==p['label']]
            best=max(choices, default=(0,0))
            if best[0]>=threshold:
                unmatched.remove(-best[1]); add(p['label'], 'tp')
            else: add(p['label'], 'fp')
        for i in unmatched: add(truth[i]['label'], 'fn')
    def metrics(c):
        tp, fp, fn=c['tp'],c['fp'],c['fn']
        return dict(**c, precision=tp/(tp+fp) if tp+fp else None, recall=tp/(tp+fn) if tp+fn else None,
                    f1=2*tp/(2*tp+fp+fn) if 2*tp+fp+fn else None)
    total={k:sum(c[k] for c in counts.values()) for k in ('tp','fp','fn')}
    return {'images':len(images),'iou_threshold':threshold,'score_threshold':score,'micro':metrics(total),
            'per_class':{k:metrics(v) for k,v in sorted(counts.items())}, 'dataset_kind':dataset.get('dataset_kind','unspecified')}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('dataset',type=Path)
    parser.add_argument('--iou',type=float,default=.5)
    parser.add_argument('--score',type=float,default=.35)
    args=parser.parse_args()
    print(json.dumps(evaluate(json.loads(args.dataset.read_text()),args.iou,args.score),indent=2,allow_nan=False))

if __name__=='__main__': main()
