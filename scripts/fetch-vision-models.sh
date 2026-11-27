#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
dir=vision/src/main/assets/models
mkdir -p "$dir"
curl --fail --location --retry 3 --max-time 300 'https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/float32/1/efficientdet_lite0.tflite' -o "$dir/efficientdet.tflite.tmp"
python3 - "$dir/efficientdet.tflite.tmp" <<'PY'
import sys
from pathlib import Path
p=Path(sys.argv[1]); assert p.stat().st_size > 100000
with p.open('rb') as f: assert f.read(8)[4:8] == b'TFL3'
PY
mv "$dir/efficientdet.tflite.tmp" "$dir/efficientdet.tflite"
curl --fail --location --retry 3 --max-time 300 'https://huggingface.co/onnx-community/depth-anything-v2-small/resolve/main/onnx/model_quantized.onnx' -o "$dir/depth.onnx.tmp"
echo "fcf51f1b230362b28690bb9d1809bf0431f29cad20534e3f589bd7285547f20d  $dir/depth.onnx.tmp" | sha256sum -c -
mv "$dir/depth.onnx.tmp" "$dir/depth.onnx"
curl --fail --location --retry 3 --max-time 300 'https://github.com/Megvii-BaseDetection/YOLOX/releases/download/0.1.1rc0/yolox_nano.onnx' -o "$dir/yolox_nano.onnx.tmp"
echo "c789161ed43c8269fcd4e67c67eeeb4e80c622da2eb296a20bc6007bd18a0b7d  $dir/yolox_nano.onnx.tmp" | sha256sum -c -
mv "$dir/yolox_nano.onnx.tmp" "$dir/yolox_nano.onnx"
curl --fail --location --retry 3 --max-time 60 'https://raw.githubusercontent.com/Megvii-BaseDetection/YOLOX/0.1.1rc0/LICENSE' -o "$dir/YOLOX_LICENSE.txt"
grep -q 'Apache License' "$dir/YOLOX_LICENSE.txt"
sha256sum "$dir/efficientdet.tflite" "$dir/depth.onnx" "$dir/yolox_nano.onnx" > "$dir/MODEL_SHA256.txt"
