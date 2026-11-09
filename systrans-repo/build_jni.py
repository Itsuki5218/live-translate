#!/usr/bin/env python3
"""Link the JNI wrappers against the static whisper/llama archives."""
import os
import subprocess
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
NDK = os.environ.get("ANDROID_NDK_ROOT") or os.path.join(ROOT, "sdk", "android-ndk")
BIN = os.path.join(NDK, "toolchains", "llvm", "prebuilt",
                   "windows-x86_64" if os.name == "nt" else "linux-x86_64", "bin")
CXX = os.path.join(BIN, "clang++.exe" if os.name == "nt" else "clang++")
STRIP = os.path.join(BIN, "llvm-strip.exe" if os.name == "nt" else "llvm-strip")
SYSROOT = os.path.join(NDK, "toolchains", "llvm", "prebuilt",
                       "windows-x86_64" if os.name == "nt" else "linux-x86_64", "sysroot")

ARCH = os.path.join(ROOT, "build", "archives")
OUT = os.path.join(ROOT, "app", "src", "main", "jniLibs", "arm64-v8a")
TMP = os.path.join(ROOT, "build", "tmp")
os.makedirs(TMP, exist_ok=True)
ENV = dict(os.environ)
ENV["TMP"] = TMP
ENV["TEMP"] = TMP


def run(cmd):
    print(">>", os.path.basename(cmd[0]), os.path.basename(cmd[-1]), flush=True)
    p = subprocess.run(cmd, capture_output=True, env=ENV)
    if p.returncode != 0:
        print(p.stdout.decode("utf-8", "replace")[-3000:], flush=True)
        print(p.stderr.decode("utf-8", "replace")[-8000:], flush=True)
        sys.exit(1)


def link(src, includes, libs, out_name):
    os.makedirs(OUT, exist_ok=True)
    out = os.path.join(OUT, out_name)
    cmd = [CXX, "--target=aarch64-linux-android29", "--sysroot=" + SYSROOT,
           "-shared", "-fPIC", "-O2", "-fvisibility=hidden",
           "-Wl,-Bsymbolic", "-Wl,--exclude-libs,ALL",
           "-static-libstdc++", "-Wl,--start-group"]
    for inc in includes:
        cmd += ["-I", inc]
    cmd += [src]
    cmd += libs
    cmd += ["-Wl,--end-group", "-llog", "-o", out]
    run(cmd)
    run([STRIP, "--strip-unneeded", out])
    print("  ->", out_name, os.path.getsize(out), flush=True)


def main():
    link(os.path.join(ROOT, "app", "jni", "whisper_jni.cpp"),
         [os.path.join(ROOT, "third_party", "whisper.cpp", "include"),
          os.path.join(ROOT, "third_party", "whisper.cpp", "ggml", "include")],
         [os.path.join(ARCH, "whisper-libwhisper.a"),
          os.path.join(ARCH, "whisper-libggml.a"),
          os.path.join(ARCH, "whisper-libggml-cpu.a"),
          os.path.join(ARCH, "whisper-libggml-base.a")],
         "libwhisperjni.so")

    link(os.path.join(ROOT, "app", "jni", "llama_jni.cpp"),
         [os.path.join(ROOT, "third_party", "llama.cpp", "include"),
          os.path.join(ROOT, "third_party", "llama.cpp", "ggml", "include")],
         [os.path.join(ARCH, "llama-libllama.a"),
          os.path.join(ARCH, "llama-libggml.a"),
          os.path.join(ARCH, "llama-libggml-cpu.a"),
          os.path.join(ARCH, "llama-libggml-base.a")],
         "libllamajni.so")

    print("JNI OK", flush=True)


if __name__ == "__main__":
    main()
