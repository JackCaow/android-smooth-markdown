#!/usr/bin/env python3
"""Build the owned std-only parser and handwritten JNI bridge; no Cargo bridge crates.

Release consumers receive binaries in the AAR. Only source builders need Rust/NDK.
"""
import argparse
import concurrent.futures
import os
from pathlib import Path
import platform
import shutil
import struct
import subprocess
import sys
import tempfile
import zipfile

if sys.version_info < (3, 5):
    sys.exit('Rust parser source builds require Python 3.5 or newer')

ROOT = Path(__file__).resolve().parents[1]
RUST = ROOT / 'rust-core'
SHIM = ROOT / 'smoothmarkdown-core/src/main/cpp/smooth_markdown_rust_jni.c'
BUILD = ROOT / 'smoothmarkdown/build/generated/rust'
ABIS = {
    'arm64-v8a': ('aarch64-linux-android', 'aarch64-linux-android'),
    'armeabi-v7a': ('armv7-linux-androideabi', 'armv7a-linux-androideabi'),
    'x86': ('i686-linux-android', 'i686-linux-android'),
    'x86_64': ('x86_64-linux-android', 'x86_64-linux-android'),
}

def run(args, env=None):
    print('+ ' + ' '.join(map(str,args)), flush=True)
    subprocess.run(list(map(str,args)),env=env,check=True)

def sdk_path():
    value=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    if value:return Path(value)
    properties=ROOT/'local.properties'
    if properties.exists():
        for line in properties.read_text().splitlines():
            if line.startswith('sdk.dir='):return Path(line.split('=',1)[1].replace('\\:', ':').replace('\\\\','\\'))
    if platform.system()=='Darwin':return Path.home()/'Library/Android/sdk'
    return Path.home()/'Android/Sdk'

def ndk_path(explicit):
    value=explicit or os.environ.get('ANDROID_NDK_HOME') or os.environ.get('ANDROID_NDK_ROOT')
    if value:path=Path(value)
    else:
        candidates=list((sdk_path()/'ndk').glob('*'))
        candidates=[p for p in candidates if p.name.split('.')[0].isdigit() and int(p.name.split('.')[0])>=28]
        if not candidates:raise RuntimeError('Install Android NDK 28+: sdkmanager "ndk;28.2.13676358"')
        path=max(candidates,key=lambda p:tuple(int(x) for x in p.name.split('.')))
    props=(path/'source.properties').read_text()
    revision=next(line.split('=',1)[1].strip() for line in props.splitlines() if line.startswith('Pkg.Revision'))
    if int(revision.split('.')[0])<28:raise RuntimeError('Rust native builds require NDK 28+ for 16KB page support')
    return path

def check_elf(path, abi=None):
    data=Path(path).read_bytes()
    if data[:4]!=b'\x7fELF' or data[5]!=1:raise RuntimeError('{}: unsupported ELF'.format(path))
    bits=data[4]
    if abi is not None:
        machine=struct.unpack_from('<H',data,18)[0]
        expected={'arm64-v8a':183,'armeabi-v7a':40,'x86':3,'x86_64':62}[abi]
        if machine!=expected:raise RuntimeError('{}: ELF machine {} does not match {}'.format(path, machine, abi))
    if bits==2:
        phoff=struct.unpack_from('<Q',data,32)[0];size,count=struct.unpack_from('<HH',data,54)
    elif bits==1:
        phoff=struct.unpack_from('<I',data,28)[0];size,count=struct.unpack_from('<HH',data,42)
    else:raise RuntimeError('Invalid ELF class')
    aligns=[]
    for i in range(count):
        start=phoff+i*size
        if struct.unpack_from('<I',data,start)[0]==1:
            alignment=struct.unpack_from('<Q' if bits==2 else '<I',data,start+(48 if bits==2 else 28))[0]
            if alignment<16384:raise RuntimeError('{}: LOAD alignment {} < 16384'.format(path, alignment))
            aligns.append(alignment)
    if not aligns:raise RuntimeError('{}: no ELF LOAD segments'.format(path))
    print('16KB ELF verified: {}, LOAD alignments {}'.format(path.name, aligns),flush=True)

def build_android(abi,ndk):
    rust_target,clang_target=ABIS[abi]
    host={'Darwin':'darwin-x86_64','Linux':'linux-x86_64','Windows':'windows-x86_64'}[platform.system()]
    toolbin=ndk/'toolchains/llvm/prebuilt'/host/'bin'
    clang=toolbin/(clang_target+'24-clang'+('.cmd' if platform.system()=='Windows' else ''))
    if not clang.exists():raise RuntimeError('NDK compiler is missing: {}'.format(clang))
    output=BUILD/'jniLibs'/abi;output.mkdir(parents=True,exist_ok=True)
    cargo_dir=BUILD/'cargo'/abi
    env=os.environ.copy();env['CARGO_TARGET_DIR']=str(cargo_dir)
    env['CARGO_TARGET_'+rust_target.upper().replace('-','_')+'_LINKER']=str(clang)
    env['RUSTFLAGS']='-C link-arg=-Wl,-z,max-page-size=16384 -C link-arg=-Wl,-z,common-page-size=16384'
    run(['cargo','build','--manifest-path',RUST/'Cargo.toml','--release','--target',rust_target,'--offline','--locked'],env)
    static=cargo_dir/rust_target/'release/libsmooth_markdown_rust.a'
    library=output/'libsmooth_markdown_rust_jni.so'
    run([clang,'-shared','-fPIC','-O2','-fvisibility=hidden','-I'+str(RUST/'include'),SHIM,static,
         '-Wl,--exclude-libs,ALL','-Wl,--no-undefined','-Wl,-z,max-page-size=16384','-Wl,-z,common-page-size=16384',
         '-ldl','-lm','-llog','-latomic','-o',library])
    run([toolbin/('llvm-strip'+('.exe' if platform.system()=='Windows' else '')),'--strip-unneeded',library])
    check_elf(library,abi)

def check_aar(path):
    with zipfile.ZipFile(str(path)) as archive, tempfile.TemporaryDirectory(prefix='smooth-rust-aar-') as temporary:
        expected={'jni/{}/libsmooth_markdown_rust_jni.so'.format(abi) for abi in ABIS}
        actual={name for name in archive.namelist() if name.endswith('/libsmooth_markdown_rust_jni.so')}
        if actual!=expected:raise RuntimeError('{}: expected four ABI JNI entries, got {}'.format(path, sorted(actual)))
        for abi in ABIS:
            binary=Path(temporary)/abi/'libsmooth_markdown_rust_jni.so'
            binary.parent.mkdir();binary.write_bytes(archive.read('jni/{}/libsmooth_markdown_rust_jni.so'.format(abi)))
            check_elf(binary,abi)
    print('AAR verified: {} contains all four matching ABIs with 16KB ELF alignment'.format(path),flush=True)

def java_home(explicit):
    value=explicit or os.environ.get('JAVA_HOME')
    if value and (Path(value)/'include/jni.h').exists():return Path(value)
    javac=shutil.which('javac')
    if javac:
        value=Path(javac).resolve().parents[1]
        if (value/'include/jni.h').exists():return value
    if platform.system()=='Darwin':
        # Some bundled JBR runtimes lack headers. JNI uses the stable C ABI, so
        # the system JDK's headers may compile a bridge loaded by a newer JVM.
        found=subprocess.run(['/usr/libexec/java_home'],stdout=subprocess.PIPE,stderr=subprocess.PIPE,universal_newlines=True)
        if found.returncode==0:
            value=Path(found.stdout.strip())
            if (value/'include/jni.h').exists():return value
    studio=Path('/Applications/Android Studio.app/Contents/jbr/Contents/Home')
    if (studio/'include/jni.h').exists():return studio
    raise RuntimeError('JAVA_HOME must point to a JDK with include/jni.h')

def build_host(jdk):
    system=platform.system()
    if system not in ('Darwin','Linux'):raise RuntimeError('Host JNI tests currently support macOS/Linux')
    output=BUILD/'host';output.mkdir(parents=True,exist_ok=True)
    env=os.environ.copy();env['CARGO_TARGET_DIR']=str(BUILD/'cargo/host')
    # Android target linker flags must never leak into the desktop build.
    env.pop('RUSTFLAGS',None)
    env.pop('CARGO_BUILD_TARGET',None)
    run(['cargo','build','--manifest-path',RUST/'Cargo.toml','--release','--offline','--locked'],env)
    static=BUILD/'cargo/host/release/libsmooth_markdown_rust.a'
    include=java_home(jdk)/'include'
    library=output/('libsmooth_markdown_rust_jni.dylib' if system=='Darwin' else 'libsmooth_markdown_rust_jni.so')
    cc=shutil.which('clang') or shutil.which('cc')
    if not cc:raise RuntimeError('A host C compiler is required')
    args=[cc,'-dynamiclib' if system=='Darwin' else '-shared','-fPIC','-O2','-I'+str(include),'-I'+str(include/('darwin' if system=='Darwin' else 'linux')),'-I'+str(RUST/'include'),SHIM,static,'-o',library]
    if system=='Darwin':args+=['-framework','Security','-framework','CoreFoundation','-liconv','-lSystem']
    else:args+=['-ldl','-lm','-lpthread']
    run(args)
    print('Host JNI: '+str(library),flush=True)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--android',action='store_true');parser.add_argument('--host',action='store_true')
    parser.add_argument('--ndk');parser.add_argument('--java-home');parser.add_argument('--abi',action='append',choices=ABIS)
    parser.add_argument('--check-elf',type=Path)
    parser.add_argument('--check-aar',type=Path)
    args=parser.parse_args()
    if args.check_elf:check_elf(args.check_elf);return
    if args.check_aar:check_aar(args.check_aar);return
    if not args.android and not args.host:args.android=True
    if args.host:build_host(args.java_home)
    if args.android:
        ndk=ndk_path(args.ndk)
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as workers:
            for future in [workers.submit(build_android,abi,ndk) for abi in args.abi or ABIS]:future.result()
if __name__=='__main__':
    try:main()
    except (RuntimeError,subprocess.CalledProcessError,OSError) as error:
        print('Rust parser build failed: '+str(error),file=sys.stderr);sys.exit(1)
