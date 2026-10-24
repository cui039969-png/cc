"""Create a source-only archive. Never includes local records, logs, tools or Git history."""
from pathlib import Path
import subprocess
import sys
import zipfile
root = Path(__file__).resolve().parents[1]
subprocess.run([sys.executable, str(root / 'scripts' / 'check_sensitive.py'), '--self-test'], cwd=root, check=True)
paths = subprocess.check_output(["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"], cwd=root).decode("utf-8").split("\0")
output = root / ".local" / "delivery" / "daily-3-minutes-source.zip"
output.parent.mkdir(parents=True, exist_ok=True)
forbidden = {".git", ".local", ".tools", ".gradle", ".kotlin", "build", "private", "personal-data", "backups"}
with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
    for name in sorted(set(paths)):
        p = root / name
        if not name or not p.is_file() or forbidden.intersection(Path(name).parts): continue
        if p.suffix.lower() in {".jks", ".keystore", ".db", ".sqlite", ".log", ".apk", ".aab"} or p.name == 'local.properties' or p.name == '.env' or p.name.startswith('.env.'):
            raise SystemExit("Refusing sensitive artifact: " + name)
        archive.write(p, "daily-3-minutes/" + name)
        if name == 'gradlew':
            archive.getinfo('daily-3-minutes/' + name).create_system = 3
            archive.getinfo('daily-3-minutes/' + name).external_attr = 0o100755 << 16
print(output)
