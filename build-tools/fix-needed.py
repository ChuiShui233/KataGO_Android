#!/usr/bin/env python3
"""Patch ELF NEEDED entry: replace absolute libOpenCL.so path with soname."""
import pathlib
import sys

default = pathlib.Path(__file__).resolve().parents[1] / "KataGO_Android/app/src/main/jniLibs/arm64-v8a/libkatago_opencl.so"
path = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else default

# fallback for legacy path
if not path.exists():
    legacy = pathlib.Path("/media/sd/Kata-android/kataA/app/src/main/jniLibs/arm64-v8a/libkatago_opencl.so")
    if legacy.exists():
        path = legacy

with open(path, "rb") as f:
    data = bytearray(f.read())

candidates = [
    b"/media/sd/Kata-android/kataA/app/src/main/jniLibs/arm64-v8a/libOpenCL.so",
    str(path.parent / "libOpenCL.so").encode(),
]
old = None
for c in candidates:
    if c in data:
        old = c
        break
if old is None:
    old = candidates[0]

new = b"libOpenCL.so"
new_padded = new + b"\x00" * (len(old) - len(new))

idx = data.find(old)
if idx < 0:
    if b"libOpenCL.so" in data:
        print("Already patched or short soname present, nothing to do")
        sys.exit(0)
    print(f"String not found in binary! searched: {old!r}")
    sys.exit(1)

data[idx:idx+len(old)] = new_padded

with open(path, "wb") as f:
    f.write(data)

print(f"Patched at offset 0x{idx:x}: {old.decode()!r} -> {new.decode()!r} in {path}")
