import hashlib, re, sys, zipfile
from pathlib import Path
apk, badging = map(Path, sys.argv[1:])
text = badging.read_text()
assert re.search(r"package: name='com\.omega\.ai' versionCode='28' versionName='1\.0\.18'", text), text[:300]
assert "sdkVersion:'26'" in text and "targetSdkVersion:'35'" in text
assert "arm64-v8a" in text
with zipfile.ZipFile(apk) as z:
    assert z.testzip() is None
    names=set(z.namelist())
    assert {'AndroidManifest.xml','classes.dex','lib/arm64-v8a/libomega_jni.so','assets/models/efficientdet.tflite','assets/models/depth.onnx','assets/models/yolox_nano.onnx','assets/models/YOLOX_LICENSE.txt'} <= names
    assert z.getinfo('assets/models/efficientdet.tflite').file_size>100000
    depth=z.read('assets/models/depth.onnx')
    assert hashlib.sha256(depth).hexdigest()=='fcf51f1b230362b28690bb9d1809bf0431f29cad20534e3f589bd7285547f20d'
    yolo=z.read('assets/models/yolox_nano.onnx')
    assert hashlib.sha256(yolo).hexdigest()=='c789161ed43c8269fcd4e67c67eeeb4e80c622da2eb296a20bc6007bd18a0b7d'
    assert b'Apache License' in z.read('assets/models/YOLOX_LICENSE.txt')
print('PASS: APK ZIP CRC, package version, SDK, ABI, JNI and model assets; signature verified separately by apksigner')
