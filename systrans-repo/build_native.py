#!/usr/bin/env python3
"""Cross-compile whisper.cpp and llama.cpp for Android arm64 into app/jniLibs.

Requires ANDROID_NDK_ROOT pointing at an NDK r26+ and CMake+Ninja.
Third-party sources are fetched from GitHub releases (see docs/BUILD.md).
"""
import glob
import os
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
NDK = os.environ.get("ANDROID_NDK_ROOT") or os.path.join(ROOT, "sdk", "android-ndk")
CMAKE = os.environ.get("CMAKE") or "cmake"
NINJA = os.environ.get("NINJA") or "ninja"
TOOLCHAIN = os.path.join(NDK, "build", "cmake", "android.toolchain.cmake")

WHISPER_SRC = os.environ.get("WHISPER_SRC") or os.path.join(ROOT, "third_party", "whisper.cpp")
LLAMA_SRC = os.environ.get("LLAMA_SRC") or os.path.join(ROOT, "third_party", "llama.cpp")

ARCH = os.path.join(ROOT, "build", "archives")
OUT = os.path.join(ROOT, "app", "src", "main", "jniLibs", "arm64-v8a")

COMMON = [
    "-G", "Ninja",
    "-DCMAKE_MAKE_PROGRAM=" + NINJA,
    "-DCMAKE_TOOLCHAIN_FILE=" + TOOLCHAIN,
    "-DANDROID_ABI=arm64-v8a",
    "-DANDROID_PLATFORM=android-29",
    "-DCMAKE_BUILD_TYPE=Release",
    "-DBUILD_SHARED_LIBS=OFF",
    "-DGGML_OPENMP=OFF",
    "-DGGML_NATIVE=OFF",
    "-DCMAKE_C_FLAGS=-fvisibility=hidden",
    "-DCMAKE_CXX_FLAGS=-fvisibility=hidden",
]


def run(cmd):
    print(">>", " ".join(cmd[:6]), "...", flush=True)
    p = subprocess.run(cmd, capture_output=True)
    if p.returncode != 0:
        print(p.stdout.decode("utf-8", "replace")[-3000:], flush=True)
        print(p.stderr.decode("utf-8", "replace")[-6000:], flush=True)
        sys.exit(p.returncode)


def build(name, src, extra, targets):
    b = os.path.join(ROOT, "build", name + "-static")
    run([CMAKE, "-S", src, "-B", b] + COMMON + extra)
    run([CMAKE, "--build", b, "--target"] + targets + ["-j", "8"])
    return b


def collect(build_dir, tag):
    os.makedirs(ARCH, exist_ok=True)
    n = 0
    for dirpath, _, files in os.walk(build_dir):
        for f in files:
            if f.endswith(".a"):
                shutil.copyfile(os.path.join(dirpath, f), os.path.join(ARCH, tag + "-" + f))
                n += 1
    print(tag, "archives:", n, flush=True)


def main():
    for p in (WHISPER_SRC, LLAMA_SRC):
        if not os.path.isdir(p):
            print("missing third-party source:", p)
            print("see docs/BUILD.md")
            sys.exit(1)

    wb = build("whisper", WHISPER_SRC,
               ["-DWHISPER_BUILD_TESTS=OFF", "-DWHISPER_BUILD_EXAMPLES=OFF",
                "-DWHISPER_BUILD_SERVER=OFF"],
               ["whisper", "ggml"])
    collect(wb, "whisper")

    lb = build("llama", LLAMA_SRC,
               ["-DLLAMA_BUILD_TESTS=OFF", "-DLLAMA_BUILD_EXAMPLES=OFF",
                "-DLLAMA_BUILD_TOOLS=OFF", "-DLLAMA_BUILD_SERVER=OFF", "-DLLAMA_BUILD_APP=OFF"],
               ["llama", "ggml"])
    collect(lb, "llama")

    print("done. now run build_jni.py", flush=True)


if __name__ == "__main__":
    main()
