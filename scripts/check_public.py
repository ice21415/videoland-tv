"""Reject common accidental personal artifacts before publishing. Never print matched values."""
from pathlib import Path
import re
import subprocess
import sys

root = Path(__file__).resolve().parents[1]
tracked = subprocess.run(['git', 'ls-files', '-z'], cwd=root, capture_output=True, check=True).stdout
files = [root / name.decode('utf-8') for name in tracked.split(b'\0') if name]
assert files, 'No tracked files to inspect'
patterns = {
    'private network address': re.compile(rb'\b(?:192\.168\.\d{1,3}\.\d{1,3}|10\.\d{1,3}\.\d{1,3}\.\d{1,3}|172\.(?:1[6-9]|2\d|3[01])\.\d{1,3}\.\d{1,3}|100\.(?:6[4-9]|[7-9]\d|1[01]\d|12[0-7])\.\d{1,3}\.\d{1,3})\b'),
    'personal Windows path': re.compile(rb'[A-Za-z]:[\\/](?:Users|wei)[\\/]', re.I),
    'private key': re.compile(rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'),
    'GitHub access token': re.compile(rb'\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})\b'),
    'saved JWT': re.compile(rb'\beyJ[A-Za-z0-9_-]{15,}\.[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}\b'),
}
failed = False
for path in files:
    rel = path.relative_to(root).as_posix()
    brand_assets = {'app/src/main/res/drawable-mdpi/videoland_wordmark.png',
                    'app/src/main/res/drawable-xxxhdpi/videoland_square.png'}
    if (path.suffix.lower() in {'.apk', '.aab', '.jks', '.keystore', '.p12', '.png', '.jpg', '.log', '.hprof'} and rel not in brand_assets) or path.name == 'local.properties':
        print(f'Forbidden publication artifact: {rel}')
        failed = True
    if path.suffix.lower() == '.jar':
        if rel != 'gradle/wrapper/gradle-wrapper.jar':
            print(f'Unexpected binary: {rel}')
            failed = True
        continue
    data = path.read_bytes()
    for label, pattern in patterns.items():
        if pattern.search(data):
            print(f'Privacy check failed: {label} in {rel}')
            failed = True
if failed:
    sys.exit(1)
print(f'Public-source checks passed: {len(files)} files; no matching private artifacts.')
