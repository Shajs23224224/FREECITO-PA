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
sha256sum "$dir/efficientdet.tflite" "$dir/depth.onnx" > "$dir/MODEL_SHA256.txt"
