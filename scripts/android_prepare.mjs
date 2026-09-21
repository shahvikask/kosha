#!/usr/bin/env node
/**
 * After `npx tauri android init`, stamp Android bits.
 * GUD_TEST_APK=1 (GUD-Android-APK only) marks a sideloadable test build.
 */
import { existsSync, mkdirSync, readFileSync, writeFileSync, cpSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

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
    `dependencies {
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("com.android.billingclient:billing-ktx:7.1.1")
    implementation("com.google.android.play:integrity:1.4.0")
    implementation("com.google.android.play:review-ktx:2.0.2")
`,
  );
}
if (!gradle.includes('review-ktx')) {
  gradle = gradle.replace(
    /implementation\("com.google.android.play:integrity:[^"]+"\)/,
    `implementation("com.google.android.play:integrity:1.4.0")
    implementation("com.google.android.play:review-ktx:2.0.2")`,
  );
}
if (testApk) {
  if (!gradle.includes('buildConfig = true') && !gradle.includes('buildConfig = true')) {
    gradle = gradle.replace(
      /defaultConfig\s*\{/,
      `defaultConfig {
        buildConfigField("boolean", "GUD_TEST_APK", "true")
`,
    );
  }
  if (!gradle.includes('buildFeatures')) {
    gradle = gradle.replace(
      /android\s*\{/,
      `android {
    buildFeatures {
        buildConfig = true
    }
`,
    );
  }
}
if (!testApk && !gradle.includes('isMinifyEnabled = true')) {
  gradle = gradle.replace(
    /getByName\("release"\)\s*\{/,
    `getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
`,
  );
}
if (!gradle.includes('proguard-rules.pro') && gradle.includes('isMinifyEnabled = true')) {
  gradle = gradle.replace(
    /isMinifyEnabled = true/,
    `isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")`,
  );
}
writeFileSync(appGradle, gradle);

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

console.log(testApk ? 'Android overlay applied (TEST APK — sideload trial on).' : 'Android overlay applied (Play AAB — sideload locked).');
