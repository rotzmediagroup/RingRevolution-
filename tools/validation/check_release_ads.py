"""Check the actual generated release BuildConfig, not just intended Gradle defaults."""
from pathlib import Path
import re
root = Path(__file__).resolve().parents[2]
source = (root/'app/build/generated/source/buildConfig/release/com/shiostudios/dumplingrings/BuildConfig.java').read_text()
assert re.search(r'boolean ADS_ENABLED\s*=\s*true;', source), 'Release ads are disabled'
assert re.search(r'boolean ADS_USE_TEST_IDS\s*=\s*false;', source), 'Release uses test mode'
props = dict(line.split('=', 1) for line in (root/'admob.properties').read_text().splitlines() if line and not line.startswith('#'))
for field, prop in [('AD_UNIT_REWARDED', 'ADMOB_REWARDED_ID'), ('AD_UNIT_INTERSTITIAL', 'ADMOB_INTERSTITIAL_ID'), ('AD_UNIT_BANNER', 'ADMOB_BANNER_ID')]:
    value = re.search(r'String '+field+r'\s*=\s*"([^"]+)";', source).group(1)
    assert value == props[prop], (field, 'does not match owner unit')
    assert '3940256099942544' not in value, 'Google sample unit in release'
print('PASS: release ads enabled, test mode off, all three owner ad units verified in generated BuildConfig.')
