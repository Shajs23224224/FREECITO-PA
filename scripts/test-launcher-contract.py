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
assert 'FreeFireLauncher.open(this)' in ui and 'INICIAR IA' in ui and 'TrainingCalibrationActivity' not in ui and 'TrainingProfile.load' not in ui
assert 'Settings.ACTION_ACCESSIBILITY_SETTINGS' in ui and 'TouchBridge.startObservation()' in ui
binding=(java/'game/GameBinding.kt').read_text()
assert 'FreeFireTrainingAdapter()' in binding and 'accessibilityTestScopeConfirmed()==true' in binding
service=next(s for s in manifest.findall('application/service') if s.get(ns+'name')=='.accessibility.OmegaTouchService')
assert service.get(ns+'permission')=='android.permission.BIND_ACCESSIBILITY_SERVICE'
xml=ET.parse(base/'res/xml/omega_touch_service.xml').getroot()
assert xml.get(ns+'canTakeScreenshot')=='true' and xml.get(ns+'packageNames')=='com.dts.freefireth'
adapter=(java/'game/FreeFireTrainingAdapter.kt').read_text()
assert 'TrainingObservation.automaticMarker' in adapter and 'consecutive>=2' in adapter and 'tracker.update' in adapter and 'TouchBridge.submit' in adapter
assert not json.loads((root/'config/architecture.json').read_text())['free_fire']['gameplay_validated']
diagnostics=(java/'SessionDiagnostics.kt').read_text()
service_code=(java/'accessibility/OmegaTouchService.kt').read_text()
assert 'Ver diagnóstico de última sesión' in ui and 'SessionDiagnostics.report()' in ui
assert 'previous_process_interrupted' in diagnostics and 'entries.size>100' in diagnostics
assert 'imageWorker.execute' in service_code and 'screenshot_timeout' in service_code and 'foreground_or_geometry_timeout' in service_code
assert 'waiting_training_marker' in adapter and 'waiting_hud' in adapter
print('PASS: automatic HUD training adapter, explicit session and screenshot/gesture source contract; not a device test')
