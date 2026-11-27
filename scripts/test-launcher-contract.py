from pathlib import Path
import xml.etree.ElementTree as ET
import json
root=Path(__file__).resolve().parents[1]
base=root/'app/src/main'
manifest=ET.parse(base/'AndroidManifest.xml').getroot()
ns='{http://schemas.android.com/apk/res/android}'
assert [p.get(ns+'name') for p in manifest.findall('queries/package')]==['com.dts.freefireth']
assert not any(p.get(ns+'name')=='android.permission.QUERY_ALL_PACKAGES' for p in manifest.findall('uses-permission'))
java=base/'java/com/omega/ai'
ui=(java/'MainActivity.kt').read_text()
assert 'FreeFireLauncher.open(this)' in ui and 'INICIAR IA' in ui and 'TrainingCalibrationActivity' in ui
assert 'Settings.ACTION_ACCESSIBILITY_SETTINGS' in ui and 'TouchBridge.startObservation()' in ui
binding=(java/'game/GameBinding.kt').read_text()
assert 'FreeFireTrainingAdapter(profile)' in binding and 'accessibilityTestScopeConfirmed()==true' in binding
service=next(s for s in manifest.findall('application/service') if s.get(ns+'name')=='.accessibility.OmegaTouchService')
assert service.get(ns+'permission')=='android.permission.BIND_ACCESSIBILITY_SERVICE'
xml=ET.parse(base/'res/xml/omega_touch_service.xml').getroot()
assert xml.get(ns+'canTakeScreenshot')=='true' and xml.get(ns+'packageNames')=='com.dts.freefireth'
adapter=(java/'game/FreeFireTrainingAdapter.kt').read_text()
assert 'TrainingObservation.markerPresent' in adapter and 'consecutive<2' in adapter and 'TouchBridge.submit' in adapter
assert not json.loads((root/'config/architecture.json').read_text())['free_fire']['gameplay_validated']
print('PASS: calibrated training adapter, explicit session and screenshot/gesture source contract; not a device test')
