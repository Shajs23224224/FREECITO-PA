from pathlib import Path
import numpy as np
import onnxruntime as ort
p=Path(__file__).resolve().parents[1]/'vision/src/main/assets/models/yolox_nano.onnx'
o=ort.SessionOptions()
o.intra_op_num_threads=1
o.inter_op_num_threads=1
s=ort.InferenceSession(str(p),sess_options=o,providers=['CPUExecutionProvider'])
assert len(s.get_inputs())==len(s.get_outputs())==1
i=s.get_inputs()[0]
out=s.get_outputs()[0]
assert i.name=='images' and i.shape==[1,3,416,416] and i.type=='tensor(float)'
assert out.name=='output' and out.shape==[1,3549,85] and out.type=='tensor(float)'
v=s.run(['output'],{'images':np.full((1,3,416,416),114,dtype=np.float32)})[0]
assert v.shape==(1,3549,85) and np.isfinite(v).all()
assert np.min(v[:,:,4:])>=0 and np.max(v[:,:,4:])<=1
print('PASS: official YOLOX-Nano ONNX CPU inference and raw head contract; constant input only')
