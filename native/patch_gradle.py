from pathlib import Path
import sys, re

p = Path(sys.argv[1])
s = p.read_text()

dep = 'implementation "androidx.health.connect:connect-client:1.1.0"'
if dep not in s:
    m = re.search(r'dependencies\s*\{', s)
    if not m:
        raise SystemExit("Could not find dependencies block in app/build.gradle")
    insert_at = m.end()
    s = s[:insert_at] + "\n    " + dep + s[insert_at:]

p.write_text(s)
print("Health Connect AndroidX dependency added.")
