#!/usr/bin/env python3
"""Summarize *interoperability signatures* of an independently downloaded APK.

Never publishes ONYX bytecode, reconstructed source, embedded URLs, API keys, or
method bodies. Official APK is stored in /tmp on the GitHub Actions runner.
"""
from __future__ import annotations
import collections
import hashlib
import re
import sys
import zipfile
from pathlib import Path

apk = Path(sys.argv[1])
root = Path(sys.argv[2])
target = "OnyxFilmsProvider"
all_java = list(root.rglob("*.java"))
sha = hashlib.sha256(apk.read_bytes()).hexdigest()

print("# ONYX TV v1.8.16 — static interoperability signatures")
print()
print("Generated from the verified official **TV** APK on an isolated GitHub Actions runner.")
print("The APK and decompiled code are not published or committed. A binary analysis")
print("does **not** confer permission to reuse proprietary provider implementations.")
print()
print("## Binary identity and multimedia components")
print()
print(f"- SHA-256: \`{sha}\`")
print(f"- Size: {apk.stat().st_size:,} bytes")
with zipfile.ZipFile(apk) as z:
    names = z.namelist()
    dex = [n for n in names if re.fullmatch(r"classes\\d*\\.dex", n)]
    libs = [n for n in names if n.startswith("lib/") and n.endswith(".so")]
    print(f"- DEX: {len(dex)}; native libraries: {len(libs)}")
    print(f"- ABIs: {', '.join(sorted(set(x.split('/')[1] for x in libs))) or 'none'}")
    av = sorted(set(Path(s).name for s in libs if re.search(r"(media3|avcodec|avutil|swscale|ffmpeg|subtitle|vlc)", s, re.I)))
    print(f"- Multimedia library names: {', '.join(av) or 'none'}")
print(f"- Decompiler-emitted Java classes: {len(all_java)}")
print()

# Public interoperability surface is limited to names/signatures, not method bodies.
providers = [p for p in all_java if p.stem.endswith("Provider") or p.stem.endswith("Source")]
extractors = [p for p in all_java if p.stem.endswith("Extractor")]
models = [p for p in all_java if p.stem in ("MainAPI", "ExtractorApi", "BaseProvider", "Provider", "SourceProvider", "Video", "ExtractorLink")]
print("## Component families")
print()
print(f"- Provider/source classes: {len(providers)}")
print(f"- Extractor classes: {len(extractors)}")
print(f"- API/model classes by known names: {len(models)}")
print()
print("### Provider identifiers (class names only, capped)")
for p in sorted(providers, key=lambda x:x.stem.lower())[:115]:
    print(f"- \`{p.stem}\`")
print()
print("### Extractor identifiers (class names only, capped)")
for p in sorted(extractors, key=lambda x:x.stem.lower())[:110]:
    print(f"- \`{p.stem}\`")
print()

sig_re = re.compile(
    r"(?m)^\\s*(?:@Override\\s*)?(?:(?:public|protected|private|static|final|synchronized|abstract)\\s+){0,6}"
    r"(?:[\\w<>\\[\\].?, ]{1,130}\\s+)?"
    r"([A-Za-z_$][\\w$]*)\\s*\\(([^(){}]{0,170})\\)\\s*(?:throws\\s+[\\w., ]+)?\\s*\\{"
)
class_re = re.compile(r"(?m)\\b(?:class|interface)\\s+(\\w+)\\b([^\\{\\n]{0,180})")
interesting = re.compile(r"load|search|link|extract|source|episode|season|subtit|server|quality|play|mainpage|home|video|request|error|update|provider|init", re.I)

def summarize(p: Path) -> None:
    src = p.read_text("utf-8", errors="replace")
    declaration = next((m.group(0) for m in class_re.finditer(src) if m.group(1) == p.stem), "")
    superclasses = re.sub(r"\\s+", " ", declaration.replace("{", "")).strip()[:200]
    print(f"#### \`{p.stem}\`")
    print(f"- Declaration: \`{superclasses}\`" if superclasses else "- Declaration not parsed")
    imports = re.findall(r"(?m)^import\\s+(?:static\\s+)?([\\w.]+)\\s*;", src)
    # Package-level dependency hints, no embedded endpoints or source bodies.
    hints = sorted(set(x.split(".")[0] for x in imports if x.startswith(("okhttp", "retrofit", "org.jsoup", "androidx.media3", "kotlinx", "com.streamflix"))))
    print(f"- Import family hints: {', '.join(hints) if hints else 'not determined'}")
    match = []
    for m in sig_re.finditer(src):
        name, params = m.groups()
        if name == p.stem or interesting.search(name):
            param_count = 0 if not params.strip() else params.count(",") + 1
            match.append(f"{name}({param_count} parameter(s))")
    uniq = list(dict.fromkeys(match))
    print(f"- Callable method-name/arity indicators: {', '.join(uniq[:28]) if uniq else 'not determined by this parser'}")
    if len(uniq) > 28:
        print(f"- Additional signatures omitted: {len(uniq)-28}")
    print()

print("## Targeted provider and API anatomy")
print()
targets = sorted(
    (p for p in all_java if p.stem in {
        "OnyxFilmsProvider","MainAPI","ExtractorApi","BaseProvider",
        "Provider","SourceProvider","ArchiveOrgProvider","PlexProvider",
        "PlutoTVProvider","FilemoonExtractor","TmdbApi","TmdbProvider"
    }), key=lambda x: x.stem
)
for p in targets[:24]:
    summarize(p)
print("## Interoperability and licensing conclusion")
print()
print("- These declarations are reverse-engineered compatibility clues, **not** reusable library licenses.")
print("- Official public ONYX GitHub repository currently exposes README and usage PDF only, not source code, SDK, API specification or a license granting source reuse.")
print("- Provider behavior cannot be called directly from MYO merely because its classes appear in an APK; private protocol details and secrets are intentionally excluded.")
print("- No ONYX-specific source was integrated solely on the basis of this report.")
print("- Any independent implementation must be grounded in documented and permitted endpoints, with functional stream tests before being labeled usable.")
