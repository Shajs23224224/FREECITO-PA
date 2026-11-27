from pathlib import Path
import xml.etree.ElementTree as ET
import json
root=Path(__file__).resolve().parents[1]
base=root/'app/src/main'
manifest=ET.parse(base/'AndroidManifest.xml').getroot()
ns='{http://schemas.android.com/apk/res/android}'
assert [p.get(ns+'name') for p in manifest.findall('queries/package')]==['com.dts.freefireth']
assert not any(p.get(ns+'name')=='android.permission.QUERY_ALL_PACKAGES' for p in manifest.findall('uses-permission'))
launcher=(base/'java/com/omega/ai/game/FreeFireLauncher.kt').read_text()
assert 'const val PACKAGE="com.dts.freefireth"' in launcher
assert 'getLaunchIntentForPackage(PACKAGE)' in launcher and 'activity.startActivity(intent)' in launcher
ui=(base/'java/com/omega/ai/MainActivity.kt').read_text()
assert 'FreeFireLauncher.open(this)' in ui and 'ABRIR FREE FIRE' in ui
assert 'val adapter: AuthorizedGameAdapter? = null' in (base/'java/com/omega/ai/game/GameBinding.kt').read_text()
config=json.loads((root/'config/architecture.json').read_text())
assert not config['free_fire']['capture_enabled'] and not config['free_fire']['gameplay_validated']
service=next(s for s in manifest.findall('application/service') if s.get(ns+'name')=='.accessibility.OmegaTouchService')
assert service.get(ns+'permission')=='android.permission.BIND_ACCESSIBILITY_SERVICE'
assert 'Settings.ACTION_ACCESSIBILITY_SETTINGS' in ui
assert 'getEnabledAccessibilityServiceList' in ui and 'TouchBridge.connected()' in ui and 'TouchBridge.isArmed()' in ui
assert 'Configurar accesibilidad (opcional)' in ui
assert config['accessibility']['settings_link'] and not config['accessibility']['public_client_enabled']
print('PASS: Free Fire launch-only source contract, narrow package visibility, adapter disabled')
