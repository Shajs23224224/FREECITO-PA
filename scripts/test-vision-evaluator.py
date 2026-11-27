import importlib.util
import json
from pathlib import Path
root=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('evaluator',root/'scripts/evaluate-vision-offline.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
d=json.loads((root/'tests/fixtures/offline-vision-synthetic.json').read_text())
r=m.evaluate(d)
assert r['micro']==dict(tp=1,fp=2,fn=1,precision=1/3,recall=.5,f1=.4)
assert r['per_class']['cup']['tp']==1 and r['per_class']['cup']['fp']==1
assert r['per_class']['book']['precision'] is None
assert r['per_class']['chair']['recall'] is None
assert m.evaluate({'images':[{'id':'empty','truth':[],'predictions':[]}]})['micro']['f1'] is None
for bad in ({'images':[]},{'images':[d['images'][0],d['images'][0]]}):
    try: m.evaluate(bad)
    except ValueError: pass
    else: raise AssertionError('invalid dataset accepted')
assert m.iou([0,0,1,1],[.5,.5,1,1])==.25
print('PASS: synthetic offline evaluation, one-to-one matches, duplicates, missed objects, empty cases and undefined metrics; no model accuracy claim')
