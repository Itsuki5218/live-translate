#!/usr/bin/env python3
"""Build the APK without Gradle.

Requires:
  * JDK 17+ on JAVA_HOME
  * Android SDK build-tools 34.0.0 and platform android-34
  * A prebuilt tools-r8.jar in the repo root (see docs/BUILD.md)
  * native libs already placed in app/src/main/jniLibs/arm64-v8a
"""
import glob
import os
import shutil
import subprocess
import sys
import zipfile

ROOT = os.path.dirname(os.path.abspath(__file__))
APP = os.path.join(ROOT, "app")
BUILD = os.path.join(ROOT, "build")

SDK = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME") \
    or os.path.join(ROOT, "sdk")
BUILD_TOOLS = os.environ.get("BUILD_TOOLS") or os.path.join(SDK, "build-tools", "34.0.0")
PLATFORM = os.environ.get("ANDROID_JAR") or os.path.join(SDK, "platforms", "android-34", "android.jar")
JDK = os.environ.get("JAVA_HOME") or ""
R8 = os.environ.get("R8_JAR") or os.path.join(ROOT, "tools-r8.jar")

AAPT2 = os.path.join(BUILD_TOOLS, "aapt2.exe" if os.name == "nt" else "aapt2")
ZIPALIGN = os.path.join(BUILD_TOOLS, "zipalign.exe" if os.name == "nt" else "zipalign")
APKSIGNER = os.path.join(BUILD_TOOLS, "apksigner.bat" if os.name == "nt" else "apksigner")
JAVAC = os.path.join(JDK, "bin", "javac.exe" if os.name == "nt" else "javac")
JAVA = os.path.join(JDK, "bin", "java.exe" if os.name == "nt" else "java")
KEYTOOL = os.path.join(JDK, "bin", "keytool.exe" if os.name == "nt" else "keytool")

PACKAGE = "io.github.livetranslate"


def run(cmd):
    print(">>", " ".join(cmd), flush=True)
    p = subprocess.run(cmd, capture_output=True)
    out = p.stdout.decode("utf-8", "replace")
    err = p.stderr.decode("utf-8", "replace")
    if out.strip():
        print(out[-3000:], flush=True)
    if p.returncode != 0:
        print(err[-6000:], flush=True)
        sys.exit(p.returncode)


def main():
    for path, name in ((PLATFORM, "android.jar"), (AAPT2, "aapt2"), (R8, "tools-r8.jar")):
        if not os.path.exists(path):
            print("missing %s: %s" % (name, path))
            print("see docs/BUILD.md for setup")
            sys.exit(1)

    if os.path.isdir(BUILD):
        shutil.rmtree(BUILD)
    for d in ("gen", "classes", "dex"):
        os.makedirs(os.path.join(BUILD, d))

    run([AAPT2, "compile", "--dir", os.path.join(APP, "res"),
         "-o", os.path.join(BUILD, "res.zip")])

    run([AAPT2, "link", "-o", os.path.join(BUILD, "base.apk"),
         "-I", PLATFORM,
         "--manifest", os.path.join(APP, "AndroidManifest.xml"),
         "-R", os.path.join(BUILD, "res.zip"),
         "--java", os.path.join(BUILD, "gen"),
         "--min-sdk-version", "29",
         "--target-sdk-version", "34",
         "--auto-add-overlay"])

    sources = sorted(glob.glob(os.path.join(APP, "src", "**", "*.java"), recursive=True))
    sources += sorted(glob.glob(os.path.join(BUILD, "gen", "**", "*.java"), recursive=True))
    with open(os.path.join(BUILD, "sources.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(sources))

    run([JAVAC, "-encoding", "UTF-8", "--release", "11",
         "-classpath", PLATFORM,
         "-d", os.path.join(BUILD, "classes"),
         "@" + os.path.join(BUILD, "sources.txt")])

    classes = sorted(glob.glob(os.path.join(BUILD, "classes", "**", "*.class"), recursive=True))
    run([JAVA, "-cp", R8, "com.android.tools.r8.D8",
         "--min-api", "29", "--lib", PLATFORM,
         "--output", os.path.join(BUILD, "dex")] + classes)

    unsigned = os.path.join(BUILD, "unsigned.apk")
    shutil.copy(os.path.join(BUILD, "base.apk"), unsigned)

    libs = sorted(glob.glob(os.path.join(APP, "src", "main", "jniLibs", "**", "*.so"),
                            recursive=True))
    with zipfile.ZipFile(unsigned, "a", zipfile.ZIP_DEFLATED) as z:
        z.write(os.path.join(BUILD, "dex", "classes.dex"), "classes.dex")
        for so in libs:
            abi = os.path.basename(os.path.dirname(so))
            z.write(so, "lib/%s/%s" % (abi, os.path.basename(so)))
    print("native libs packed:", len(libs), flush=True)

    aligned = os.path.join(BUILD, "aligned.apk")
    run([ZIPALIGN, "-f", "-p", "4", unsigned, aligned])

    ks = os.path.join(ROOT, "debug.keystore")
    if not os.path.exists(ks):
        run([KEYTOOL, "-genkeypair", "-v", "-keystore", ks,
             "-alias", "androiddebugkey", "-keyalg", "RSA", "-keysize", "2048",
             "-validity", "10000", "-storepass", "android", "-keypass", "android",
             "-dname", "CN=Android Debug,O=Android,C=US"])

    out = os.path.join(ROOT, "livetranslate.apk")
    run([APKSIGNER, "sign", "--ks", ks,
         "--ks-pass", "pass:android", "--key-pass", "pass:android",
         "--out", out, aligned])

    print("\nOK ->", out, os.path.getsize(out), "bytes")


if __name__ == "__main__":
    main()
