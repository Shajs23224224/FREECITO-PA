"""Optional CPU model contract test; synthetic uniform RGB input, not depth accuracy."""
from pathlib import Path
import numpy as np
import onnxruntime as ort
p=Path(__file__).resolve().parents[1]/'vision/src/main/assets/models/depth.onnx'
o=ort.SessionOptions();o.intra_op_num_threads=1;o.inter_op_num_threads=1
s=ort.InferenceSession(str(p),sess_options=o,providers=['CPUExecutionProvider'])
i=next(i for i in s.get_inputs() if i.name=='pixel_values')
assert i.type=='tensor(float)' and len(i.shape)==4
for actual, expected in zip(i.shape,[1,3,518,518]):
    assert not isinstance(actual,int) or actual==expected
assert any(out.name=='predicted_depth' for out in s.get_outputs())
mean=np.array([.485,.456,.406],dtype=np.float32).reshape(1,3,1,1)
std=np.array([.229,.224,.225],dtype=np.float32).reshape(1,3,1,1)
x=np.broadcast_to((.5-mean)/std,(1,3,518,518)).copy()
y=s.run(['predicted_depth'],{'pixel_values':x})[0]
assert y.ndim==3 and y.shape[0]==1 and all(8<=n<=1024 for n in y.shape[1:]) and np.isfinite(y).all()
print('PASS: relative depth ONNX CPU input/output contract on synthetic uniform input; not metric depth validation')
