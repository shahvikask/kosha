#!/usr/bin/env node
/**
 * After `npx tauri android init`, stamp Android bits.
 * GUD_TEST_APK=1 (GUD-Android-APK only) marks a sideloadable test build.
 * Also embeds the on-device Python casparser engine via Chaquopy.
 */
import { existsSync, mkdirSync, readFileSync, writeFileSync, cpSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execSync } from 'node:child_process';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const android = join(root, 'src-tauri', 'gen', 'android');
const overlay = join(root, 'src-tauri', 'android-overlay');
const testApk = process.env.GUD_TEST_APK === '1' || process.env.VITE_GUD_TEST_APK === '1';

if (!existsSync(android)) {
  console.error('Run npx tauri android init first (CI does this).');
  process.exit(1);
}

const appGradle = join(android, 'app', 'build.gradle.kts');
let gradle = readFileSync(appGradle, 'utf8');
gradle = gradle.replace(/compileSdk\s*=\s*\d+/, 'compileSdk = 36');
gradle = gradle.replace(/targetSdk\s*=\s*\d+/, 'targetSdk = 36');
if (!gradle.includes('billing-ktx')) {
  gradle = gradle.replace(
    /dependencies\s*\{/,
    `dependencies {\n    implementation("androidx.security:security-crypto:1.1.0-alpha06")\n    implementation("androidx.documentfile:documentfile:1.0.1")\n    implementation("com.android.billingclient:billing-ktx:7.1.1")\n    implementation("com.google.android.play:integrity:1.4.0")\n    implementation("com.google.android.play:review-ktx:2.0.2")\n`,
  );
}
if (!gradle.includes('review-ktx')) {
  gradle = gradle.replace(
    /implementation\("com.google.android.play:integrity:[^"]+"\)/,
    `implementation("com.google.android.play:integrity:1.4.0")\n    implementation("com.google.android.play:review-ktx:2.0.2")`,
  );
}
if (testApk) {
  if (!gradle.includes('buildConfig = true') && !gradle.includes('buildConfig = true')) {
    gradle = gradle.replace(
      /defaultConfig\s*\{/,
      `defaultConfig {\n        buildConfigField("boolean", "GUD_TEST_APK", "true")\n`,
    );
  }
  if (!gradle.includes('buildFeatures')) {
    gradle = gradle.replace(
      /android\s*\{/,
      `android {\n    buildFeatures {\n        buildConfig = true\n    }\n`,
    );
  }
}
if (!testApk && !gradle.includes('isMinifyEnabled = true')) {
  gradle = gradle.replace(
    /getByName\("release"\)\s*\{/,
    `getByName("release") {\n            isMinifyEnabled = true\n            isShrinkResources = true\n`,
  );
}
if (!gradle.includes('proguard-rules.pro') && gradle.includes('isMinifyEnabled = true')) {
  gradle = gradle.replace(
    /isMinifyEnabled = true/,
    `isMinifyEnabled = true\n            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")`,
  );
}
if (!gradle.includes('com.chaquo.python')) {
  gradle = gradle.replace(
    /plugins\s*\{/,
    `plugins {\n    id("com.chaquo.python")\n`,
  );
}
if (!gradle.includes('chaquopy {')) {
  gradle += `\n\nchaquopy {\n    defaultConfig {\n        version = "3.12"\n        pip {\n            options("--find-links", "src/main/python-wheels")\n            install("pydantic>=2.3.0,<3")\n            install("python-dateutil>=2.8.2,<3")\n            install("casparser-isin>=2025.3.1")\n            install("pypdfium2==5.13.0")\n        }\n    }\n}\n`;
}
writeFileSync(appGradle, gradle);

function injectChaquopyPlugin(filePath, groovy) {
  if (!existsSync(filePath)) return;
  let text = readFileSync(filePath, 'utf8');
  if (text.includes('com.chaquo.python')) {
    writeFileSync(filePath, text);
    return;
  }
  if (!text.includes('mavenCentral()')) {
    text = text.replace(
      /pluginManagement\s*\{/,
      `pluginManagement {\n    repositories {\n        mavenCentral()\n        google()\n        gradlePluginPortal()\n    }\n`,
    );
  }
  if (text.includes('plugins {')) {
    text = text.replace(
      /plugins\s*\{/,
      groovy
        ? `plugins {\n    id "com.chaquo.python" version "17.0.0" apply false\n`
        : `plugins {\n    id("com.chaquo.python") version "17.0.0" apply false\n`,
    );
  } else if (text.includes('pluginManagement {')) {
    text += groovy
      ? `\nplugins { id "com.chaquo.python" version "17.0.0" apply false }\n`
      : `\nplugins { id("com.chaquo.python") version "17.0.0" apply false }\n`;
  }
  writeFileSync(filePath, text);
}

injectChaquopyPlugin(join(android, 'settings.gradle.kts'), false);
injectChaquopyPlugin(join(android, 'settings.gradle'), true);
injectChaquopyPlugin(join(android, 'build.gradle.kts'), false);
injectChaquopyPlugin(join(android, 'build.gradle'), true);

const proguardSrc = join(overlay, 'app', 'proguard-rules.pro');
const proguardDst = join(android, 'app', 'proguard-rules.pro');
if (existsSync(proguardSrc)) writeFileSync(proguardDst, readFileSync(proguardSrc));

const manifest = join(android, 'app', 'src', 'main', 'AndroidManifest.xml');
if (existsSync(manifest)) {
  let m = readFileSync(manifest, 'utf8');
  if (!m.includes('android:allowBackup')) {
    m = m.replace(
      '<application',
      '<application android:allowBackup="false" android:fullBackupContent="false" android:dataExtractionRules="@xml/data_extraction_rules" android:networkSecurityConfig="@xml/network_security_config" android:hasFragileUserData="true"',
    );
  }
  if (!m.includes('com.android.vending.BILLING')) {
    m = m.replace(
      /<application/,
      '    <uses-permission android:name="com.android.vending.BILLING" />\n    <application',
    );
  }
  writeFileSync(manifest, m);
}

const xmlSrc = join(overlay, 'app', 'src', 'main', 'res', 'xml');
const xmlDst = join(android, 'app', 'src', 'main', 'res', 'xml');
mkdirSync(xmlDst, { recursive: true });
cpSync(xmlSrc, xmlDst, { recursive: true });

const ktSrc = join(overlay, 'app', 'src', 'main', 'java');
const ktDst = join(android, 'app', 'src', 'main', 'java');
mkdirSync(ktDst, { recursive: true });
cpSync(ktSrc, ktDst, { recursive: true });

const pyDst = join(android, 'app', 'src', 'main', 'python');
mkdirSync(pyDst, { recursive: true });
cpSync(join(root, 'src', 'casparser-main', 'casparser'), join(pyDst, 'casparser'), { recursive: true });
for (const name of ['cas_parse_server.py', 'cas_android.py', 'eod_prices.py']) {
  const src = join(root, 'scripts', name);
  if (existsSync(src)) cpSync(src, join(pyDst, name));
}

const wheels = join(android, 'app', 'src', 'main', 'python-wheels');
mkdirSync(wheels, { recursive: true });
const wheelName = 'pypdfium2-5.13.0-py3-none-android_23_arm64_v8a.whl';
const wheelPath = join(wheels, wheelName);
if (!existsSync(wheelPath)) {
  const url = `https://github.com/pypdfium2-team/pypdfium2/releases/download/5.13.0/${wheelName}`;
  console.log('Downloading', wheelName);
  execSync(`curl -fsSL -o "${wheelPath}" "${url}"`, { stdio: 'inherit' });
}

console.log(testApk ? 'Android overlay applied (TEST APK — on-device Python casparser).' : 'Android overlay applied (Play AAB — on-device Python casparser).');
