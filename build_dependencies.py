import argparse
import os
import shutil
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("--fresh", action="store_true", help="Clean generated native outputs before building.")
parser.add_argument("--native-only", action="store_true",
                    help="Skip fetching/building dependencies and only rebuild the native code (ndk-build).")
args = parser.parse_args()

from build_dependencies.common import log, error
from build_dependencies.pdfium import fetch_prebuilt_pdfium
from build_dependencies.libpng import build_libpng_libs
from build_dependencies.freetype2 import build_freetype_libs
from build_dependencies.shared_cpp_lib import copy_shared_cpp_libs
from build_dependencies.native_code import build_native_code
from build_dependencies.values import LIB_DIR_PATH, ARCH_NAMES, FILE_NAMES, LIB_EXTENSION, Lib


def clean_native_outputs():
    log("Cleaning native dependency outputs.")
    paths_to_clean = [
        Path("build_dependencies/build"),
        Path("build_dependencies/libpng_build"),
        Path("build_dependencies/freetype_build"),
        Path("PdfiumAndroid/src/main/libs"),
        Path("PdfiumAndroid/src/main/obj"),
        Path("PdfiumAndroid/src/main/jni/libs"),
        Path("PdfiumAndroid/src/main/jni/obj"),
    ]
    for path in paths_to_clean:
        if path.exists():
            shutil.rmtree(path)

    for lib in Path("PdfiumAndroid/src/main").glob("**/*.so"):
        lib.unlink()


def verify_native_deps():
    required = [FILE_NAMES[lib] + LIB_EXTENSION
                for lib in (Lib.pdfium, Lib.freetype2, Lib.libpng, Lib.cpp_shared)]
    missing = [str(Path(LIB_DIR_PATH) / arch_name / lib_filename)
               for arch_name in ARCH_NAMES.values()
               for lib_filename in required
               if not (Path(LIB_DIR_PATH) / arch_name / lib_filename).exists()]

    if missing:
        log("Missing prebuilt dependencies (run a full build first, without --native-only):")
        for path in missing:
            log("  " + path)
        error("Cannot build native code because dependency libraries are missing.")


log("Start " + __file__)
log("INSTALL_PATH: " + LIB_DIR_PATH)

if args.fresh:
    clean_native_outputs()

if args.native_only:
    verify_native_deps()

os.chdir("build_dependencies")
if not args.native_only:
    fetch_prebuilt_pdfium()
    build_libpng_libs()
    build_freetype_libs()
    copy_shared_cpp_libs()
build_native_code()
