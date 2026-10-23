#!/usr/bin/env python3
"""Build a test APK with installed official Android SDK tools; no Gradle download.

Usage: python build_local.py --sdk PATH [--check-api28]
Requires Java 17 or 21 and SDK Platform 35 / Build Tools 35.0.0.
For strict API 28 checking, also install SDK Platform 28.
"""
from pathlib import Path
import argparse
import os
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--sdk', default=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT'))
parser.add_argument('--check-api28', action='store_true')
parser.add_argument('--instrumentation', action='store_true', help='Also build the optional emulator test package')
args = parser.parse_args()
if not args.sdk:
    parser.error('Supply --sdk or ANDROID_HOME')
SDK = Path(args.sdk).resolve()
BT = SDK / 'build-tools' / '35.0.0'
OUT = ROOT / 'build' / 'standalone'
OUT.mkdir(parents=True, exist_ok=True)
JAVA = shutil.which('java')
KEYTOOL = shutil.which('keytool')
if not JAVA or not KEYTOOL:
    sys.exit('Java 17+ and keytool must be in PATH')
def run(*cmd):
    subprocess.run([str(x) for x in cmd], check=True, cwd=ROOT)
def executable(name):
    return BT / (name + ('.exe' if os.name == 'nt' else ''))

android35 = SDK / 'platforms' / 'android-35' / 'android.jar'
android28 = SDK / 'platforms' / 'android-28' / 'android.jar'
for required in [android35, BT/'lib'/'d8.jar', BT/'lib'/'apksigner.jar']:
    if not required.exists(): sys.exit('Missing SDK tool: '+str(required))
if args.check_api28 and not android28.exists():
    sys.exit('API 28 platform is required for --check-api28')

ET.register_namespace('android','http://schemas.android.com/apk/res/android')
manifest=ET.parse(ROOT/'app/src/main/AndroidManifest.xml')
manifest.getroot().set('package','cn.wengmang.qiping')
manifest.write(OUT/'AndroidManifest.xml',encoding='utf-8',xml_declaration=True)
generated=OUT/'generated'; generated.mkdir(exist_ok=True)
classes=OUT/'classes'
if classes.exists(): shutil.rmtree(classes)
classes.mkdir()
run(executable('aapt2'),'compile','--dir',ROOT/'app/src/main/res','-o',OUT/'resources.zip')
run(executable('aapt2'),'link','-I',android35,'--manifest',OUT/'AndroidManifest.xml',
    '-o',OUT/'resources.apk','--java',generated,'-A',ROOT/'app/src/main/assets','--min-sdk-version','28','--target-sdk-version','35',
    OUT/'resources.zip')
sources=sorted((ROOT/'app/src/main/java').rglob('*.java'))+sorted(generated.rglob('*.java'))
run(JAVA,'com.sun.tools.javac.Main','-encoding','UTF-8','-source','8','-target','8',
    '-Xlint:-options','-bootclasspath',android28 if args.check_api28 else android35,
    '-d',classes,*sources)
with zipfile.ZipFile(OUT/'classes.jar','w',zipfile.ZIP_DEFLATED) as jar:
    for item in classes.rglob('*.class'):jar.write(item,item.relative_to(classes).as_posix())
run(JAVA,'-cp',BT/'lib/d8.jar','com.android.tools.r8.D8','--min-api','28','--lib',android35,
    '--output',OUT,OUT/'classes.jar')
shutil.copyfile(OUT/'resources.apk',OUT/'unsigned.apk')
with zipfile.ZipFile(OUT/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as apk:
    apk.write(OUT/'classes.dex','classes.dex')
run(executable('zipalign'),'-f','4',OUT/'unsigned.apk',OUT/'aligned.apk')
signing=ROOT/'test-signing'; signing.mkdir(exist_ok=True)
key=signing/'qiping-test.jks'
if not key.exists():
    run(KEYTOOL,'-genkeypair','-keystore',key,'-storepass','android','-keypass','android',
        '-alias','qiping-test','-keyalg','RSA','-keysize','2048','-validity','10000','-storetype','JKS',
        '-dname','CN=Qiping Test, O=Development, C=CN')
apk=OUT/'QipingLauncher-1.0.0-test.apk'
run(JAVA,'-jar',BT/'lib/apksigner.jar','sign','--ks',key,'--ks-key-alias','qiping-test',
    '--ks-pass','pass:android','--key-pass','pass:android',
    '--min-sdk-version','28','--v1-signing-enabled','true','--v2-signing-enabled','true',
    '--out',apk,OUT/'aligned.apk')
run(JAVA,'-jar',BT/'lib/apksigner.jar','verify','--verbose','--min-sdk-version','28',apk)
run(executable('zipalign'),'-c','4',apk)
test=OUT/'tests'; test.mkdir(exist_ok=True)
run(JAVA,'com.sun.tools.javac.Main','-encoding','UTF-8','-d',test,
    ROOT/'app/src/main/java/cn/wengmang/qiping/Favorites.java',ROOT/'tests/FavoritesTest.java')
run(JAVA,'-cp',test,'cn.wengmang.qiping.FavoritesTest')
run(JAVA,'com.sun.tools.javac.Main','-encoding','UTF-8','-cp',android35,'-d',test,
    ROOT/'app/src/main/java/cn/wengmang/qiping/ShortcutStore.java',ROOT/'tests/ShortcutStoreTest.java')
run(JAVA,'-cp',str(test)+os.pathsep+str(android35),'cn.wengmang.qiping.ShortcutStoreTest')
run(JAVA,'com.sun.tools.javac.Main','-encoding','UTF-8','-cp',str(test)+os.pathsep+str(android35),'-d',test,
    ROOT/'app/src/main/java/cn/wengmang/qiping/DisplayPreferences.java',ROOT/'tests/DisplayPreferencesTest.java')
run(JAVA,'-cp',str(test)+os.pathsep+str(android35),'cn.wengmang.qiping.DisplayPreferencesTest')
run(JAVA,'com.sun.tools.javac.Main','-encoding','UTF-8','-cp',str(test)+os.pathsep+str(android35),'-d',test,ROOT/'app/src/main/java/cn/wengmang/qiping/ParentalStore.java',ROOT/'tests/ParentalStoreTest.java')
run(JAVA,'-cp',str(test)+os.pathsep+str(android35),'cn.wengmang.qiping.ParentalStoreTest')
run(JAVA,'com.sun.tools.javac.Main','-encoding','UTF-8','-cp',str(test)+os.pathsep+str(android35),'-d',test,
    ROOT/'app/src/main/java/cn/wengmang/qiping/ConfigBackup.java', ROOT/'app/src/main/java/cn/wengmang/qiping/WorkspaceLayout.java', ROOT/'app/src/main/java/cn/wengmang/qiping/AppEntry.java',ROOT/'app/src/main/java/cn/wengmang/qiping/ConfigStore.java',ROOT/'tests/ConfigBackupTest.java')
run(JAVA,'-cp',str(test)+os.pathsep+str(android35),'cn.wengmang.qiping.ConfigBackupTest')
run(JAVA,'com.sun.tools.javac.Main','-encoding','UTF-8','-cp',str(test)+os.pathsep+str(android35),'-d',test,
    ROOT/'app/src/main/java/cn/wengmang/qiping/AppOrganizer.java',ROOT/'tests/AppOrganizerTest.java')
run(JAVA,'-cp',str(test)+os.pathsep+str(android35),'cn.wengmang.qiping.AppOrganizerTest')
run(JAVA,'com.sun.tools.javac.Main','-encoding','UTF-8','-cp',str(test)+os.pathsep+str(android35),'-d',test,
    ROOT/'app/src/main/java/cn/wengmang/qiping/StartupState.java',ROOT/'tests/StartupStateTest.java',
    ROOT/'app/src/main/java/cn/wengmang/qiping/StartupFlow.java',ROOT/'tests/StartupFlowTest.java')
run(JAVA,'-cp',str(test)+os.pathsep+str(android35),'cn.wengmang.qiping.StartupStateTest')
run(JAVA,'-cp',str(test)+os.pathsep+str(android35),'cn.wengmang.qiping.StartupFlowTest')
run(JAVA,'com.sun.tools.javac.Main','-encoding','UTF-8','-cp',str(test)+os.pathsep+str(android35),'-d',test,
    ROOT/'app/src/main/java/cn/wengmang/qiping/WakeState.java',ROOT/'app/src/main/java/cn/wengmang/qiping/WakeFlow.java',ROOT/'tests/WakeFlowTest.java')
run(JAVA,'-cp',str(test)+os.pathsep+str(android35),'cn.wengmang.qiping.WakeFlowTest')
run(JAVA,'com.sun.tools.javac.Main','-encoding','UTF-8','-d',test,
    ROOT/'app/src/main/java/cn/wengmang/qiping/PerformancePolicy.java',ROOT/'tests/PerformancePolicyTest.java')
run(JAVA,'-cp',test,'cn.wengmang.qiping.PerformancePolicyTest')
for name in ['GridNavigation','UpdatePolicy','GlassContrast','DiagnosticJournal']:
    run(JAVA,'com.sun.tools.javac.Main','-encoding','UTF-8','-d',test,
        ROOT/('app/src/main/java/cn/wengmang/qiping/'+name+'.java'),ROOT/('tests/'+name+'Test.java'))
    run(JAVA,'-cp',test,'cn.wengmang.qiping.'+name+'Test')
print('APK:',apk)
run(JAVA,'com.sun.tools.javac.Main','-encoding','UTF-8','-cp',str(test)+os.pathsep+str(android35),'-d',test,
    ROOT/'app/src/main/java/cn/wengmang/qiping/ExperiencePolicy.java',ROOT/'tests/PersonalizationTest.java')
run(JAVA,'-cp',str(test)+os.pathsep+str(android35),'cn.wengmang.qiping.PersonalizationTest')
run(JAVA,'com.sun.tools.javac.Main','-encoding','UTF-8','-source','8','-target','8','-Xlint:-options','-d',test,
    ROOT/'app/src/main/java/cn/wengmang/qiping/PhoneWallpaperServer.java',ROOT/'tests/PhoneWallpaperServerTest.java',ROOT/'tests/PhoneBrowserHarness.java')
run(JAVA,'-cp',test,'cn.wengmang.qiping.PhoneWallpaperServerTest')
if args.instrumentation:
    target=OUT/'instrumentation'; target.mkdir(exist_ok=True)
    tree=ET.parse(ROOT/'app/src/androidTest/AndroidManifest.xml')
    tree.getroot().set('package','cn.wengmang.qiping.tests')
    ns='{http://schemas.android.com/apk/res/android}'
    ET.SubElement(tree.getroot(),'uses-sdk',{ns+'minSdkVersion':'28',ns+'targetSdkVersion':'35'})
    ET.SubElement(tree.getroot(),'instrumentation',{ns+'name':'cn.wengmang.qiping.tests.LauncherSmoke',ns+'targetPackage':'cn.wengmang.qiping'})
    tree.write(target/'AndroidManifest.xml',encoding='utf-8',xml_declaration=True)
    run(executable('aapt2'),'link','-I',android35,'--manifest',target/'AndroidManifest.xml','-o',target/'resources.apk')
    testclasses=target/'classes';testclasses.mkdir(exist_ok=True)
    run(JAVA,'com.sun.tools.javac.Main','-encoding','UTF-8','-source','8','-target','8','-Xlint:-options',
        '-bootclasspath',android28 if args.check_api28 else android35,'-d',testclasses,
        *sorted((ROOT/'app/src/androidTest/java').rglob('*.java')))
    with zipfile.ZipFile(target/'classes.jar','w',zipfile.ZIP_DEFLATED) as jar:
        for item in testclasses.rglob('*.class'):jar.write(item,item.relative_to(testclasses).as_posix())
    run(JAVA,'-cp',BT/'lib/d8.jar','com.android.tools.r8.D8','--min-api','28','--lib',android35,'--output',target,target/'classes.jar')
    shutil.copyfile(target/'resources.apk',target/'unsigned.apk')
    with zipfile.ZipFile(target/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as packed:packed.write(target/'classes.dex','classes.dex')
    run(executable('zipalign'),'-f','4',target/'unsigned.apk',target/'aligned.apk')
    test_apk=OUT/'QipingLauncher-1.0.0-androidTest.apk'
    run(JAVA,'-jar',BT/'lib/apksigner.jar','sign','--ks',key,'--ks-key-alias','qiping-test','--ks-pass','pass:android',
        '--key-pass','pass:android','--min-sdk-version','28','--out',test_apk,target/'aligned.apk')
    print('Instrumentation:',test_apk)
