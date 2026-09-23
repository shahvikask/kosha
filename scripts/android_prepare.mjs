#!/usr/bin/env node
/**
 * After `npx tauri android init`, stamp Android bits.
 * Embeds on-device Python casparser via Chaquopy.
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
    'dependencies {\n    implementation("androidx.security:security-crypto:1.1.0-alpha06")\n    implementation("androidx.documentfile:documentfile:1.0.1")\n    implementation("com.android.billingclient:billing-ktx:7.1.1")\n    implementation("com.google.android.play:integrity:1.4.0")\n    implementation("com.google.android.play:review-ktx:2.0.2")\n',
  );
}
if (!gradle.includes('com.chaquo.python')) {
  gradle = gradle.replace(/plugins\s*\{/, 'plugins {\n    id("com.chaquo.python")\n');
}
if (!gradle.includes('chaquopy {')) {
  gradle += '\nchaquopy {\n    defaultConfig {\n        version = "3.12"\n        pip {\n            options("--find-links", "src/main/python-wheels")\n            install("pydantic>=2.3.0,<3")\n            install("python-dateutil>=2.8.2,<3")\n            install("casparser-isin>=2025.3.1")\n            install("pypdfium2==5.13.0")\n        }\n    }\n}\n';
}
if (testApk && !gradle.includes('GUD_TEST_APK')) {
  gradle = gradle.replace(/defaultConfig\s*\{/, 'defaultConfig {\n        buildConfigField("boolean", "GUD_TEST_APK", "true")\n');
  if (!gradle.includes('buildFeatures')) {
    gradle = gradle.replace(/android\s*\{/, 'android {\n    buildFeatures {\n        buildConfig = true\n    }\n');
  }
}
writeFileSync(appGradle, gradle);

function injectChaquopySettings(filePath) {
  if (!existsSync(filePath)) return;
  let text = readFileSync(filePath, 'utf8');
  if (text.includes('com.chaquo.python')) {
    writeFileSync(filePath, text);
    return;
  }
  if (/pluginManagement\s*\{[\s\S]*?plugins\s*\{/.test(text)) {
    text = text.replace(
      /(pluginManagement\s*\{[\s\S]*?plugins\s*\{)/,
      '$1\n        id("com.chaquo.python") version "17.0.0"\n',
    );
  } else if (text.includes('pluginManagement')) {
    text = text.replace(
      /pluginManagement\s*\{/,
      'pluginManagement {\n    plugins {\n        id("com.chaquo.python") version "17.0.0"\n    }\n',
    );
  }
  writeFileSync(filePath, text);
}

injectChaquopySettings(join(android, 'settings.gradle'));
injectChaquopySettings(join(android, 'settings.gradle.kts'));

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
    m = m.replace(/<application/, '    <uses-permission android:name="com.android.vending.BILLING" />\n    <application');
  }
  writeFileSync(manifest, m);
}

const xmlSrc = join(overlay, 'app', 'src', 'main', 'res', 'xml');
const xmlDst = join(android, 'app', 'src', 'main', 'res', 'xml');
mkdirSync(xmlDst, { recursive: true });
if (existsSync(xmlSrc)) cpSync(xmlSrc, xmlDst, { recursive: true });

const ktSrc = join(overlay, 'app', 'src', 'main', 'java');
const ktDst = join(android, 'app', 'src', 'main', 'java');
mkdirSync(ktDst, { recursive: true });
cpSync(ktSrc, ktDst, { recursive: true });

function injectCasCommands(pluginPath) {
  if (!existsSync(pluginPath)) return;
  let kt = readFileSync(pluginPath, 'utf8');
  if (kt.includes('fun cas_parse')) {
    writeFileSync(pluginPath, kt);
    return;
  }
  if (!kt.includes('com.chaquo.python.Python')) {
    kt = kt.replace(
      'import com.google.android.play.core.review.ReviewManagerFactory',
      'import com.google.android.play.core.review.ReviewManagerFactory\nimport com.chaquo.python.Python\nimport com.chaquo.python.android.AndroidPlatform',
    );
  }
  if (!kt.includes('fun ensurePython')) {
    kt = kt.replace(
      '        connectBilling()\n    }',
      '        connectBilling()\n        try { ensurePython() } catch (_: Exception) {}\n    }\n\n    private fun ensurePython() {\n        synchronized(GrowUrDhanPlugin::class.java) {\n            if (!Python.isStarted()) {\n                Python.start(AndroidPlatform(activity.applicationContext))\n            }\n        }\n    }',
    );
  }
  kt = kt.replace(
    '    @Command\n    fun prices_refresh(invoke: Invoke) {',
    '    @Command\n    fun cas_health(invoke: Invoke) {\n        Thread {\n            try {\n                ensurePython()\n                val ready = Python.getInstance().getModule("cas_android").callAttr("health").toBoolean()\n                val out = JSObject()\n                out.put("ok", ready)\n                out.put("engine", "PYTHON_CASPARSER")\n                invoke.resolve(out)\n            } catch (e: Exception) {\n                invoke.reject(e.message ?: "Python casparser could not start on this phone.")\n            }\n        }.start()\n    }\n\n    @Command\n    fun cas_parse(invoke: Invoke) {\n        val args = try {\n            invoke.parseArgs(CasParseArgs::class.java)\n        } catch (_: Exception) {\n            invoke.reject("Missing statement file.")\n            return\n        }\n        Thread {\n            try {\n                ensurePython()\n                val json = Python.getInstance().getModule("cas_android")\n                    .callAttr("parse_b64", args.pdfBase64 ?: "", args.password ?: "", args.filename ?: "statement.pdf")\n                    .toString()\n                val out = JSObject()\n                out.put("payload", json)\n                invoke.resolve(out)\n            } catch (e: Exception) {\n                invoke.reject(e.message ?: "Python casparser could not read this statement.")\n            }\n        }.start()\n    }\n\n    @Command\n    fun prices_refresh(invoke: Invoke) {',
  );
  if (!kt.includes('class CasParseArgs')) {
    kt = kt.replace(
      /class PricesArgs \{[\s\S]*?}\s*}\s*$/,
      'class PricesArgs {\n        @JvmField\n        var isins: String = ""\n    }\n\n    @InvokeArg\n    class CasParseArgs {\n        @JvmField\n        var pdfBase64: String = ""\n        @JvmField\n        var password: String = ""\n        @JvmField\n        var filename: String = "statement.pdf"\n    }\n}\n',
    );
  }
  writeFileSync(pluginPath, kt);
}

injectCasCommands(join(ktDst, 'in/growurdhan/app/GrowUrDhanPlugin.kt'));

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
  const url = 'https://github.com/pypdfium2-team/pypdfium2/releases/download/5.13.0/' + wheelName;
  console.log('Downloading', wheelName);
  execSync('curl -fsSL -o "' + wheelPath + '" "' + url + '"', { stdio: 'inherit' });
}

console.log(testApk ? 'Android overlay applied (TEST APK - on-device Python casparser).' : 'Android overlay applied (Play AAB - on-device Python casparser).');
