import hashlib, re, sys, zipfile
from pathlib import Path
apk, badging = map(Path, sys.argv[1:])
text = badging.read_text()
assert re.search(r"package: name='com\.omega\.ai' versionCode='10' versionName='1\.0\.0'", text), text[:300]
assert "sdkVersion:'26'" in text and "targetSdkVersion:'35'" in text
assert "arm64-v8a" in text
with zipfile.ZipFile(apk) as z:
    assert z.testzip() is None
    names=set(z.namelist())
    assert {'AndroidManifest.xml','classes.dex','lib/arm64-v8a/libomega_jni.so','assets/models/efficientdet.tflite','assets/models/depth.onnx'} <= names
    assert z.getinfo('assets/models/efficientdet.tflite').file_size>100000
    depth=z.read('assets/models/depth.onnx')
    assert hashlib.sha256(depth).hexdigest()=='fcf51f1b230362b28690bb9d1809bf0431f29cad20534e3f589bd7285547f20d'
print('PASS: APK ZIP CRC, package version, SDK, ABI, JNI and model assets; signature verified separately by apksigner')
