#!/bin/bash
# Manual APK build for Shake Torch (no Gradle — the Gradle daemon cannot
# complete its client handshake in this VM, so we drive the SDK tools directly).
# Toolchain lives in ~/workspace: jdk17, android-sdk (platform 34, build-tools 34.0.0).
set -e
export JAVA_HOME=~/workspace/jdk17
export PATH=$JAVA_HOME/bin:$PATH
BT=~/workspace/android-sdk/build-tools/34.0.0
AJAR=~/workspace/android-sdk/platforms/android-34/android.jar
APP=~/workspace/shake-torch-app
BLD=$APP/manual-build
mkdir -p $BLD/gen $BLD/classes $BLD/dex

# Debug keystore (created once).
if [ ! -f $BLD/debug.keystore ]; then
  $JAVA_HOME/bin/keytool -genkeypair -keystore $BLD/debug.keystore -storepass android \
    -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US"
fi

# aapt2 needs the package attribute that AGP would normally inject from the namespace.
sed 's|<manifest xmlns:android="http://schemas.android.com/apk/res/android">|<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.summacstarter.shaketorch">|' \
  $APP/app/src/main/AndroidManifest.xml > $BLD/AndroidManifest.xml

$BT/aapt2 compile --dir $APP/app/src/main/res -o $BLD/compiled.zip
$BT/aapt2 link -o $BLD/app-res.apk -I $AJAR --manifest $BLD/AndroidManifest.xml -A $APP/app/src/main/assets \
  --java $BLD/gen --min-sdk-version 26 --target-sdk-version 34 \
  --version-code 10 --version-name 1.9 $BLD/compiled.zip

$JAVA_HOME/bin/javac -encoding UTF-8 -source 17 -target 17 -classpath $AJAR \
  -d $BLD/classes $(find $APP/app/src/main/java $BLD/gen -name "*.java")
(cd $BLD/classes && $JAVA_HOME/bin/jar cf $BLD/classes.jar .)
$BT/d8 --min-api 26 --output $BLD/dex $BLD/classes.jar

cp $BLD/app-res.apk $BLD/app-unsigned.apk
(cd $BLD/dex && zip -q $BLD/app-unsigned.apk classes.dex)
$BT/zipalign -p -f 4 $BLD/app-unsigned.apk $BLD/app-aligned.apk
$BT/apksigner sign --ks $BLD/debug.keystore --ks-pass pass:android --key-pass pass:android \
  --out $BLD/app-debug.apk $BLD/app-aligned.apk
$BT/apksigner verify $BLD/app-debug.apk
cp $BLD/app-debug.apk $APP/ShakeTorch.apk
echo "Built: $APP/ShakeTorch.apk"
