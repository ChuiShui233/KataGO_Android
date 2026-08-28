#!/usr/bin/env bash
set -euo pipefail

# relative paths, arm64-v8a only
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SRC="$ROOT/KataGo/cpp"
BUILD="$ROOT/build-android"
# NDK: env ANDROID_NDK / ANDROID_NDK_HOME, or $ROOT/ndk/*
if [ -n "${ANDROID_NDK:-}" ]; then
  NDK="$ANDROID_NDK"
elif [ -n "${ANDROID_NDK_HOME:-}" ]; then
  NDK="$ANDROID_NDK_HOME"
else
  NDK_CANDIDATE=$(ls -d "$ROOT"/ndk/android-ndk-r* 2>/dev/null | sort | tail -n1 || true)
  if [ -n "$NDK_CANDIDATE" ]; then NDK="$NDK_CANDIDATE"; else NDK="$ROOT/ndk/android-ndk-r25b"; fi
fi
NDK_SYSROOT="$NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot"
OCL_HEADERS="$ROOT/build-tools/opencl-headers"
OCL_LIB="$ROOT/KataGO_Android/app/src/main/jniLibs/arm64-v8a/libOpenCL.so"
OCL_LIB_DIR="$(dirname "$OCL_LIB")"
OUT_LIB="$ROOT/KataGO_Android/app/src/main/jniLibs/arm64-v8a/libkatago_opencl.so"

rm -rf "$BUILD"
mkdir -p "$BUILD"
cd "$BUILD"

# temp dir for libOpenCL.so to avoid absolute path in build.ninja
TMP_OCL_DIR="$BUILD/_ocl_staging"
mkdir -p "$TMP_OCL_DIR"
cp "$OCL_LIB" "$TMP_OCL_DIR/libOpenCL.so"

cmake "$SRC" \
  -DCMAKE_TOOLCHAIN_FILE="$ROOT/build-tools/android-toolchain.cmake" \
  -DNDK="$NDK" \
  -DUSE_BACKEND=OPENCL \
  -DCMAKE_BUILD_TYPE=Release \
  -DOpenCL_INCLUDE_DIR="$OCL_HEADERS" \
  -DOpenCL_LIBRARY="$TMP_OCL_DIR/libOpenCL.so" \
  -DZLIB_INCLUDE_DIR="$NDK_SYSROOT/usr/include" \
  -DZLIB_LIBRARY="$NDK_SYSROOT/usr/lib/aarch64-linux-android/29/libz.so" \
  -DCMAKE_EXE_LINKER_FLAGS="-rdynamic -L$OCL_LIB_DIR" \
  -G Ninja

# fix build.ninja linker paths
if [ -f build.ninja ]; then
  sed -i "s|$TMP_OCL_DIR/libOpenCL.so|-lOpenCL|g" build.ninja
  sed -i "s|$TMP_OCL_DIR||g" build.ninja
fi
for linkfile in $(grep -rl "$TMP_OCL_DIR" CMakeFiles/ 2>/dev/null || true); do
  sed -i "s|$TMP_OCL_DIR/libOpenCL.so|-lOpenCL|g" "$linkfile"
  sed -i "s|$TMP_OCL_DIR||g" "$linkfile"
done

ninja -j$(nproc) 2>&1

rm -rf "$TMP_OCL_DIR"

# copy to jniLibs
cp "$BUILD/katago" "$OUT_LIB" 2>/dev/null || true

# ELF patches for dlopen
python3 - "$OUT_LIB" <<'PATCH'
import struct, sys
path = sys.argv[1]
with open(path, "r+b") as f:
    data = bytearray(f.read())
e_phoff = struct.unpack_from("<Q", data, 32)[0]
e_phentsize = struct.unpack_from("<H", data, 54)[0]
e_phnum = struct.unpack_from("<H", data, 56)[0]
e_shoff = struct.unpack_from("<Q", data, 40)[0]
e_shentsize = struct.unpack_from("<H", data, 58)[0]
e_shnum = struct.unpack_from("<H", data, 60)[0]
e_shstrndx = struct.unpack_from("<H", data, 62)[0]

# fix DT_NEEDED
NEEDLE = b'_ocl_staging/libOpenCL.so'
REPL  = b'libOpenCL.so' + b'\x00' * (len(NEEDLE) - len(b'libOpenCL.so'))
idx = data.find(NEEDLE)
while idx >= 0:
    data[idx:idx+len(NEEDLE)] = REPL
    print(f"Patched DT_NEEDED at 0x{idx:x}: '{NEEDLE.decode()}' -> 'libOpenCL.so'")
    idx = data.find(NEEDLE, idx)

# clear PIE flag
for i in range(e_phnum):
    off = e_phoff + i * e_phentsize
    if struct.unpack_from("<I", data, off)[0] == 2:  # PT_DYNAMIC
        p_offset = struct.unpack_from("<Q", data, off + 8)[0]
        p_filesz = struct.unpack_from("<Q", data, off + 32)[0]
        pos = p_offset
        while pos < p_offset + p_filesz:
            tag = struct.unpack_from("<q", data, pos)[0]
            if tag == 0: break
            if tag == 0x6ffffffb:  # DT_FLAGS_1
                val = struct.unpack_from("<Q", data, pos + 8)[0]
                val &= ~0x08000000  # clear PF_1_PIE
                struct.pack_into("<Q", data, pos + 8, val)
                print(f"Patched DT_FLAGS_1: cleared PIE bit")
                break
            pos += 16
        break

# zero garbage in init_array
str_shoff = e_shoff + e_shstrndx * e_shentsize
str_offset = struct.unpack_from("<Q", data, str_shoff + 24)[0]
strtab = data[str_offset:]

# Collect all SHT_RELA relocation targets
rela_targets = set()
for i in range(e_shnum):
    sh = e_shoff + i * e_shentsize
    sh_type = struct.unpack_from("<I", data, sh + 4)[0]
    if sh_type == 4:  # SHT_RELA
        rsh_off = struct.unpack_from("<Q", data, sh + 24)[0]
        rsh_sz = struct.unpack_from("<Q", data, sh + 32)[0]
        for r in range(rsh_sz // 24):
            rela_targets.add(struct.unpack_from("<Q", data, rsh_off + r * 24)[0])

for i in range(e_shnum):
    sh = e_shoff + i * e_shentsize
    nm_idx = struct.unpack_from("<I", data, sh)[0]
    name = strtab[nm_idx:strtab.index(b'\0', nm_idx)].decode()
    if name not in (".init_array", ".preinit_array"):
        continue
    sh_off = struct.unpack_from("<Q", data, sh + 24)[0]
    sh_sz = struct.unpack_from("<Q", data, sh + 32)[0]
    fixed = 0
    for j in range(sh_sz // 8):
        e_off = sh_off + j * 8
        if struct.unpack_from("<Q", data, e_off)[0] == 0xffffffffffffffff and e_off not in rela_targets:
            struct.pack_into("<Q", data, e_off, 0)
            fixed += 1
    if fixed:
        print(f"Zeroed {fixed} garbage entries in {name}")

with open(path, "wb") as f:
    f.write(data)
PATCH

echo "=== Build complete (arm64-v8a) ==="
ls -lh "$OUT_LIB"
readelf -d "$OUT_LIB" 2>/dev/null | grep -iE 'needed|flags|soname' || true
