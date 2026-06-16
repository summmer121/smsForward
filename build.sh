#!/bin/bash
set -e

export ANDROID_HOME=/opt/android-sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/34.0.0:$PATH
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

ANDROID_JAR=$ANDROID_HOME/platforms/android-34/android.jar
PROJ=/root/MobileBalance
APP=$PROJ/app/src/main
BUILD=$PROJ/build
mkdir -p $BUILD/gen $BUILD/obj

cd $PROJ

echo ">>> 1/8 generate icon"
python3 make_icon.py

echo ">>> 2/8 aapt2 compile resources"
aapt2 compile --dir $APP/res -o $BUILD/compiled_res.zip

echo ">>> 3/8 aapt2 link"
aapt2 link -o $BUILD/app-base.apk \
    -I $ANDROID_JAR \
    --manifest $APP/AndroidManifest.xml \
    --java $BUILD/gen \
    -A $APP/assets \
    --auto-add-overlay \
    $BUILD/compiled_res.zip

echo ">>> 4/8 javac"
find $APP/java -name "*.java" > $BUILD/sources.txt
find $BUILD/gen -name "*.java" >> $BUILD/sources.txt
javac -d $BUILD/obj \
    -classpath "$ANDROID_JAR:$PROJ/libs/xposed-api.jar" \
    -source 1.8 -target 1.8 \
    -encoding UTF-8 \
    @$BUILD/sources.txt 2>&1 | tee $BUILD/javac.log
if [ ! "$(ls $BUILD/obj 2>/dev/null)" ]; then
    echo "❌ javac failed"; exit 1
fi

echo ">>> 5/8 d8 (compile to dex)"
cd $BUILD/obj
d8 --output $BUILD/ --lib $ANDROID_JAR $(find . -name "*.class")
cd $PROJ

echo ">>> 6/8 add classes.dex into APK"
cp $BUILD/app-base.apk $BUILD/app-unaligned.apk
cd $BUILD
zip -uj app-unaligned.apk classes.dex
cd $PROJ

echo ">>> 7/8 zipalign"
zipalign -f 4 $BUILD/app-unaligned.apk $BUILD/app-aligned.apk

echo ">>> 8/8 sign"
if [ ! -f $BUILD/debug.keystore ]; then
    keytool -genkey -v -keystore $BUILD/debug.keystore \
        -storepass android -alias androiddebugkey -keypass android \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=summer,O=summer,C=CN" 2>&1 | tail -3
fi

apksigner sign \
    --ks $BUILD/debug.keystore \
    --ks-pass pass:android \
    --key-pass pass:android \
    --ks-key-alias androiddebugkey \
    --out $BUILD/MobileBalance.apk \
    $BUILD/app-aligned.apk

apksigner verify $BUILD/MobileBalance.apk && echo "✅ signature ok"

ls -lh $BUILD/MobileBalance.apk
echo "✅ DONE: $BUILD/MobileBalance.apk"
