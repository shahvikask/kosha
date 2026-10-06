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
const CHAQUOPY = '17.0.0';

if (!existsSync(android)) {
  console.error('Run npx tauri android init first (CI does this).');
  process.exit(1);
}

// android init copies Tauri's default loop icon. Replace it with the brand mark.
execSync('npx tauri icon src-tauri/icons/icon.png --ios-color "#07131f"', {
  cwd: root,
  stdio: 'inherit',
});

const PLUGIN_MANAGEMENT_GROOVY = `pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id "com.chaquo.python" version "${CHAQUOPY}"
    }
}

`;

const PLUGIN_MANAGEMENT_KTS = `pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.chaquo.python") version "${CHAQUOPY}"
    }
}

`;

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
    implementation("com.google.android.gms:play-services-auth:21.3.0")
`,
  );
}
if (gradle.includes('com.chaquo.python')) {
  gradle = gradle.replace(
    /id\("com\.chaquo\.python"\)(?:\s+version\s+"[^"]+")?/,
    `id("com.chaquo.python") version "${CHAQUOPY}"`,
  );
} else {
  gradle = gradle.replace(
    /plugins\s*\{/,
    `plugins {
    id("com.chaquo.python") version "${CHAQUOPY}"
`,
  );
}
if (!gradle.includes('chaquopy {')) {
  gradle += `
chaquopy {
    defaultConfig {
        version = "3.12"
        buildPython("python3")
        pip {
            options("--find-links", "src/main/python-wheels")
            install("python-dateutil>=2.8.2,<3")
            install("typing-extensions>=4.0")
            install("pypdfium2==5.13.0")
        }
    }
}
`;
}
gradle = gradle.replace(
  /abiFilters\s*\+=\s*listOf\([^)]*armeabi-v7a[^)]*\)/g,
  'abiFilters += listOf("arm64-v8a")',
);
if (!gradle.includes('GUD_CHAQUOPY_ABI')) {
  gradle += `
// GUD_CHAQUOPY_ABI
// Keep Tauri universal + 64-bit flavors. Drop 32-bit (no Python 3.12).
androidComponents {
    beforeVariants { variant ->
        val n = variant.name
        val keep = n.startsWith("arm64") || n.startsWith("x86_64") || n.startsWith("universal")
        if (!keep) variant.enable = false
    }
}
`;
}
if (testApk && !gradle.includes('GUD_TEST_APK')) {
  gradle = gradle.replace(/defaultConfig\s*\{/, `defaultConfig {
        buildConfigField("boolean", "GUD_TEST_APK", "true")
`);
  if (!gradle.includes('buildFeatures')) {
    gradle = gradle.replace(/android\s*\{/, `android {
    buildFeatures {
        buildConfig = true
    }
`);
  }
}
writeFileSync(appGradle, gradle);

function injectChaquopySettings(filePath) {
  if (!existsSync(filePath)) return;
  let text = readFileSync(filePath, 'utf8');
  const kts = filePath.endsWith('.kts');
  const block = kts ? PLUGIN_MANAGEMENT_KTS : PLUGIN_MANAGEMENT_GROOVY;
  if (text.includes('com.chaquo.python') && text.includes('pluginManagement')) {
    writeFileSync(filePath, text);
    return;
  }
  if (/pluginManagement\s*\{[\s\S]*?plugins\s*\{/.test(text)) {
    text = text.replace(
      /(pluginManagement\s*\{[\s\S]*?plugins\s*\{)/,
      `$1\n        id${kts ? '("com.chaquo.python")' : ' "com.chaquo.python"'} version "${CHAQUOPY}"\n`,
    );
    if (!/pluginManagement\s*\{[\s\S]*?mavenCentral\(\)/.test(text)) {
      text = text.replace(
        /(pluginManagement\s*\{[\s\S]*?repositories\s*\{)/,
        `$1\n        mavenCentral()\n        google()\n        gradlePluginPortal()\n`,
      );
    }
  } else if (text.includes('pluginManagement')) {
    text = text.replace(
      /pluginManagement\s*\{/,
      `pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id${kts ? '("com.chaquo.python")' : ' "com.chaquo.python"'} version "${CHAQUOPY}"
    }
`,
    );
  } else {
    text = block + text;
  }
  writeFileSync(filePath, text);
  console.log('Chaquopy pluginManagement written to', filePath);
}

injectChaquopySettings(join(android, 'settings.gradle'));
injectChaquopySettings(join(android, 'settings.gradle.kts'));

const proguardSrc = join(overlay, 'app', 'proguard-rules.pro');
const proguardDst = join(android, 'app', 'proguard-rules.pro');
if (existsSync(proguardSrc)) writeFileSync(proguardDst, readFileSync(proguardSrc));

const manifest = join(android, 'app', 'src', 'main', 'AndroidManifest.xml');
if (existsSync(manifest)) {
  let m = readFileSync(manifest, 'utf8');
  // Chaquopy docs: start Python once per process from Application.onCreate.
  if (!m.includes('com.chaquo.python.android.PyApplication')) {
    if (/<application\b[^>]*android:name=/.test(m)) {
      m = m.replace(
        /(<application\b[^>]*?)android:name="[^"]+"/,
        '$1android:name="com.chaquo.python.android.PyApplication"',
      );
    } else {
      m = m.replace(
        '<application',
        '<application android:name="com.chaquo.python.android.PyApplication"',
      );
    }
  }
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
  if (!m.includes('WRITE_EXTERNAL_STORAGE')) {
    m = m.replace(
      /<application/,
      '    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="28" />\n    <application',
    );
  }
  if (!m.includes('android.permission.INTERNET')) {
    m = m.replace(
      /<application/,
      '    <uses-permission android:name="android.permission.INTERNET" />\n    <application',
    );
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
  if (kt.includes('fun cas_parse')) return;
  if (!kt.includes('com.chaquo.python.Python')) {
    kt = kt.replace(
      'import com.google.android.play.core.review.ReviewManagerFactory',
      'import com.google.android.play.core.review.ReviewManagerFactory\nimport com.chaquo.python.Python\nimport com.chaquo.python.android.AndroidPlatform\nimport org.json.JSONObject',
    );
  }
  if (!kt.includes('fun ensurePython')) {
    kt = kt.replace(
      '        connectBilling()\n    }',
      `        connectBilling()
        try { ensurePython() } catch (_: Exception) {}
    }

    // Chaquopy: Python.start must run on the main/Application thread.
    private fun ensurePython() {
        if (Python.isStarted()) return
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            synchronized(GrowUrDhanPlugin::class.java) {
                if (!Python.isStarted()) Python.start(AndroidPlatform(activity.applicationContext))
            }
            return
        }
        val latch = java.util.concurrent.CountDownLatch(1)
        var err: Exception? = null
        handler.post {
            try {
                synchronized(GrowUrDhanPlugin::class.java) {
                    if (!Python.isStarted()) Python.start(AndroidPlatform(activity.applicationContext))
                }
            } catch (e: Exception) {
                err = e
            } finally {
                latch.countDown()
            }
        }
        latch.await()
        err?.let { throw it }
    }`,
    );
  }
  kt = kt.replace(
    '    @Command\n    fun prices_refresh(invoke: Invoke) {',
    `    @Command
    fun cas_health(invoke: Invoke) {
        Thread {
            try {
                ensurePython()
                val raw = Python.getInstance().getModule("cas_android").callAttr("health").toString()
                val parsed = try { JSONObject(raw) } catch (_: Exception) { JSONObject() }
                val out = JSObject()
                out.put("ok", parsed.optBoolean("ok", false))
                out.put("engine", "PYTHON_CASPARSER")
                out.put("detail", raw)
                if (!parsed.optBoolean("ok", false)) {
                    val err = parsed.optString("error", "")
                    if (err.isNotBlank()) out.put("error", err)
                }
                invoke.resolve(out)
            } catch (e: Exception) {
                val out = JSObject()
                out.put("ok", false)
                out.put("engine", "PYTHON_CASPARSER")
                out.put("error", e.message ?: "Engine is not running.")
                invoke.resolve(out)
            }
        }.start()
    }

    @Command
    fun cas_parse(invoke: Invoke) {
        val args = try {
            invoke.parseArgs(CasParseArgs::class.java)
        } catch (_: Exception) {
            invoke.reject("Missing statement file.")
            return
        }
        Thread {
            try {
                ensurePython()
                val json = Python.getInstance().getModule("cas_android")
                    .callAttr("parse_b64", args.pdfBase64 ?: "", args.password ?: "", args.filename ?: "statement.pdf")
                    .toString()
                val out = JSObject()
                out.put("payload", json)
                invoke.resolve(out)
            } catch (e: Exception) {
                invoke.reject(e.message ?: "Python casparser could not read this statement.")
            }
        }.start()
    }

    @Command
    fun prices_refresh(invoke: Invoke) {`,
  );
  if (!kt.includes('class CasParseArgs')) {
    kt = kt.replace(
      /class PricesArgs \{[\s\S]*?}\s*}\s*$/,
      `class PricesArgs {
        @JvmField
        var isins: String = ""
    }

    @InvokeArg
    class CasParseArgs {
        @JvmField
        var pdfBase64: String = ""
        @JvmField
        var password: String = ""
        @JvmField
        var filename: String = "statement.pdf"
    }
}
`,
    );
  }
  writeFileSync(pluginPath, kt);
}

const pluginDst = join(ktDst, 'in/growurdhan/app/GrowUrDhanPlugin.kt');
injectCasCommands(pluginDst);
if (!existsSync(pluginDst) || !readFileSync(pluginDst, 'utf8').includes('fun cas_parse')) {
  console.error('CAS commands missing from GrowUrDhanPlugin.kt after overlay. Refusing to continue.');
  process.exit(1);
}

const pyDst = join(android, 'app', 'src', 'main', 'python');
mkdirSync(pyDst, { recursive: true });
cpSync(join(root, 'src', 'casparser-main', 'casparser'), join(pyDst, 'casparser'), { recursive: true });
for (const name of ['cas_parse_server.py', 'cas_android.py', 'eod_prices.py']) {
  const src = join(root, 'scripts', name);
  if (existsSync(src)) cpSync(src, join(pyDst, name));
}
const pydanticShim = join(root, 'scripts', 'android_pydantic.py');
if (existsSync(pydanticShim)) cpSync(pydanticShim, join(pyDst, 'pydantic.py'));
const isinShim = join(root, 'scripts', 'android_casparser_isin.py');
if (existsSync(isinShim)) cpSync(isinShim, join(pyDst, 'casparser_isin.py'));
for (const need of ['cas_android.py', 'cas_parse_server.py', 'pydantic.py', 'casparser/__init__.py']) {
  const p = join(pyDst, need);
  if (!existsSync(p)) {
    console.error('Missing on-device Python file:', p);
    process.exit(1);
  }
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

for (const rel of ['settings.gradle', 'settings.gradle.kts', 'build.gradle.kts', 'app/build.gradle.kts']) {
  const p = join(android, rel);
  if (existsSync(p)) {
    const head = readFileSync(p, 'utf8').split('\n').slice(0, 24).join('\n');
    console.log('\n--- ' + rel + ' ---\n' + head);
  }
}

console.log(testApk ? 'Android overlay applied (TEST APK - on-device Python casparser).' : 'Android overlay applied (Play AAB - on-device Python casparser).');
