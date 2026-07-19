import os
import sys

from build_dependencies.common import log
from build_dependencies.pdfium_fdroid import fetch_prebuilt_pdfium_fdroid
from build_dependencies.pdfium_source import build_pdfium_from_source
from build_dependencies.libpng import build_libpng_libs
from build_dependencies.freetype2 import build_freetype_libs
from build_dependencies.shared_cpp_lib import copy_shared_cpp_libs
from build_dependencies.native_code import build_native_code
from build_dependencies.values import LIB_DIR_PATH

log("Start " + __file__)
log("INSTALL_PATH: " + LIB_DIR_PATH)

os.chdir("build_dependencies")
if "--prebuilt-pdfium" in sys.argv:
    fetch_prebuilt_pdfium_fdroid()
else:
    build_pdfium_from_source()
build_libpng_libs()
build_freetype_libs()
copy_shared_cpp_libs()
build_native_code()
