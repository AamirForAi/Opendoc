import os
import shutil
import subprocess

from build_dependencies.common import delete_file_if_exists, error, get_lib_path, log
from build_dependencies.values import (
    Arch,
    ARCH_NAMES,
    DEPOT_TOOLS_GIT_URL,
    FILE_NAMES,
    Lib,
    LIB_EXTENSION,
    PDFIUM_BRANCH,
    PDFIUM_GIT_URL,
    PDFIUM_GN_ARGS,
    PDFIUM_GN_CPU_NAMES,
    PDFIUM_SOURCE_DIR,
)

DEPOT_TOOLS_DIR = "depot_tools"


def build_pdfium_from_source():
    root = os.getcwd()
    env = depot_tools_env(root)
    source = sync_pdfium(root, env)
    for arch in [Arch.x86, Arch.x86_64, Arch.arm64, Arch.armeabi]:
        build_arch(source, arch, env)


def depot_tools_env(root):
    depot_tools = os.path.join(root, DEPOT_TOOLS_DIR)
    if not os.path.isdir(depot_tools):
        run(["git", "clone", "--depth", "1", DEPOT_TOOLS_GIT_URL, depot_tools], root, os.environ.copy())
    env = os.environ.copy()
    env["PATH"] = depot_tools + os.pathsep + env["PATH"]
    return env


def sync_pdfium(root, env):
    checkout = os.path.join(root, PDFIUM_SOURCE_DIR)
    os.makedirs(checkout, exist_ok=True)
    if not os.path.isfile(os.path.join(checkout, ".gclient")):
        run(
            [
                "gclient", "config", "--unmanaged", PDFIUM_GIT_URL,
                "--custom-var", "checkout_configuration=minimal",
            ],
            checkout,
            env,
        )
        with open(os.path.join(checkout, ".gclient"), "a") as config:
            config.write("target_os = ['android']\n")
    run(["gclient", "sync", "-r", f"origin/{PDFIUM_BRANCH}", "--no-history", "--shallow"], checkout, env)
    return os.path.join(checkout, "pdfium")


def build_arch(source, arch, env):
    log(f"Building PDFium from source for {ARCH_NAMES[arch]}.")
    out_dir = os.path.join("out", ARCH_NAMES[arch])
    write_gn_args(os.path.join(source, out_dir), arch)
    run(["gn", "gen", out_dir], source, env)
    run(["ninja", "-C", out_dir, "pdfium"], source, env)
    install_lib(os.path.join(source, out_dir), arch)
    log(f"Finished building PDFium for {ARCH_NAMES[arch]}.")
    log("------------------------------------")


def write_gn_args(out_dir, arch):
    os.makedirs(out_dir, exist_ok=True)
    args = PDFIUM_GN_ARGS + [f'target_cpu = "{PDFIUM_GN_CPU_NAMES[arch]}"']
    with open(os.path.join(out_dir, "args.gn"), "w") as args_file:
        args_file.write("\n".join(args) + "\n")


def install_lib(out_dir, arch):
    built_lib = os.path.join(out_dir, "libpdfium.so")
    if not os.path.isfile(built_lib):
        error(f"Build produced no library at {built_lib}")
    lib_path = get_lib_path(arch, FILE_NAMES[Lib.pdfium] + LIB_EXTENSION, levels_up=1)
    delete_file_if_exists(lib_path)
    os.makedirs(os.path.dirname(lib_path), exist_ok=True)
    shutil.copy(built_lib, lib_path)
    log(f"Installed {lib_path}.")


def run(command, cwd, env):
    log("Run: " + " ".join(command))
    result = subprocess.run(command, cwd=cwd, env=env)
    if result.returncode != 0:
        error(f"'{command[0]}' failed with code {result.returncode}")


if __name__ == "__main__":
    build_pdfium_from_source()
