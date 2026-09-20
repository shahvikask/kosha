#!/usr/bin/env node
/**
 * After `npx tauri android init`, stamp Play-required Android bits:
 * targetSdk 36, no backup, Play Billing, EncryptedFile, overlay plugin.
 */
import { existsSync, mkdirSync, readFileSync, writeFileSync, cpSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const android = join(root, 'src-tauri', 'gen', 'android');
const overlay = join(root, 'src-tauri', 'android-overlay');

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
    implementation("com.android.billingclient:billing-ktx:7.1.1")
    implementation("com.google.android.play:integrity:1.4.0")
`,
  );
}
if (!gradle.includes('isMinifyEnabled = true')) {
  gradle = gradle.replace(
    /getByName\("release"\)\s*\{/,
    `getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
`,
  );
}
if (!gradle.includes('proguard-rules.pro')) {
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
      '<manifest',
      '<manifest xmlns:android="http://schemas.android.com/apk/res/android"',
    );
    if (!m.includes('com.android.vending.BILLING')) {
      m = m.replace(
        /<application/,
        '    <uses-permission android:name="com.android.vending.BILLING" />\n    <application',
      );
    }
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

console.log('Android overlay applied (API 36, no backup, Play Billing, Keystore vault).');
