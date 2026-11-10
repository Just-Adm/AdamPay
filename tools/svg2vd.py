#!/usr/bin/env python3
"""Convert Material Symbols SVGs (viewBox 0 -960 960 960) to Android VectorDrawables.
usage: svg2vd.py <symbols_dir> <out_res_drawable_dir> name[:fill] ...
"""
import re, sys, os
src, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)
for spec in sys.argv[3:]:
    fill = spec.endswith(":fill")
    name = spec.split(":")[0]
    svg = os.path.join(src, f"{name}{'-fill' if fill else ''}.svg")
    if not os.path.exists(svg):
        print("skip", name); continue
    s = open(svg).read()
    paths = re.findall(r'<path[^>]*\sd="([^"]+)"', s)
    if not paths:
        sys.exit(f"no path in {svg}")
    res = f"ic_{name}{'_fill' if fill else ''}"
    body = "\n".join(f'        <path android:fillColor="#FF000000" android:pathData="{d}"/>' for d in paths)
    xml = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Material Symbols Rounded "{name}{' (filled)' if fill else ''}", Apache-2.0 -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="960" android:viewportHeight="960"
    android:autoMirrored="false">
    <group android:translateY="960">
{body}
    </group>
</vector>
'''
    open(os.path.join(out, res + ".xml"), "w").write(xml)
print("ok", len(sys.argv) - 3)
