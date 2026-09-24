use std::fs;
use std::path::PathBuf;
use tauri::plugin::TauriPlugin;
use tauri::{AppHandle, Manager, Runtime};

struct AndroidBridge<R: Runtime> {
    plugin: tauri::plugin::PluginHandle<R>,
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(growurdhan_plugin())
        .invoke_handler(tauri::generate_handler![
            vault_blob_load,
            vault_blob_save,
            play_licence_status,
            play_purchase,
            play_restore,
            play_review,
            play_open_listing,
            cas_health,
            cas_parse,
            diag_log
        ])
        .run(tauri::generate_context!())
        .expect("GrowUrDhan failed to start");
}

fn growurdhan_plugin<R: Runtime>() -> TauriPlugin<R> {
    tauri::plugin::Builder::new("growurdhan")
        .setup(|app, api| {
            #[cfg(target_os = "android")]
            {
                let handle = api.register_android_plugin("in.growurdhan.app", "GrowUrDhanPlugin")?;
                app.manage(AndroidBridge { plugin: handle });
            }
            #[cfg(not(target_os = "android"))]
            {
                let _ = api;
            }
            Ok(())
        })
        .build()
}

fn vault_path(app: &AppHandle) -> Result<PathBuf, String> {
    let dir = app.path().app_data_dir().map_err(|e| e.to_string())?;
    fs::create_dir_all(&dir).map_err(|e| e.to_string())?;
    Ok(dir.join("vault.enc"))
}

fn phone_log<R: Runtime>(app: &AppHandle<R>, line: &str) {
    #[cfg(target_os = "android")]
    {
        if let Some(bridge) = app.try_state::<AndroidBridge<R>>() {
            let _ = bridge.plugin.run_mobile_plugin::<serde_json::Value>(
                "diag_log",
                serde_json::json!({ "line": format!("rust {line}") }),
            );
        }
    }
    #[cfg(not(target_os = "android"))]
    {
        let _ = (app, line);
    }
}

#[tauri::command]
fn diag_log<R: Runtime>(app: AppHandle<R>, line: String) -> Result<serde_json::Value, String> {
    let safe: String = line.chars().take(2000).collect();
    #[cfg(target_os = "android")]
    {
        let Some(bridge) = app.try_state::<AndroidBridge<R>>() else {
            return Ok(serde_json::json!({
                "ok": false,
                "error": "Android plugin was not registered.",
                "path": "Download/GrowUrDhan-diagnostics.txt"
            }));
        };
        return bridge
            .plugin
            .run_mobile_plugin::<serde_json::Value>("diag_log", serde_json::json!({ "line": safe }))
            .map_err(|e| e.to_string());
    }
    #[cfg(not(target_os = "android"))]
    {
        let _ = app;
        Ok(serde_json::json!({
            "ok": true,
            "path": "desktop-noop",
            "echo": safe
        }))
    }
}

#[tauri::command]
fn vault_blob_load(app: AppHandle) -> Result<Option<String>, String> {
    phone_log(&app, "vault_blob_load");
    let path = vault_path(&app)?;
    if !path.exists() {
        phone_log(&app, "vault_blob_load missing");
        return Ok(None);
    }
    let bytes = fs::read(&path).map_err(|e| e.to_string())?;
    phone_log(&app, &format!("vault_blob_load bytes={}", bytes.len()));
    Ok(Some(base64_encode(&bytes)))
}

#[tauri::command]
fn vault_blob_save(app: AppHandle, bytes_b64: String) -> Result<(), String> {
    phone_log(&app, &format!("vault_blob_save chars={}", bytes_b64.len()));
    let path = vault_path(&app)?;
    let bytes = base64_decode(&bytes_b64)?;
    fs::write(path, bytes).map_err(|e| e.to_string())
}

#[tauri::command]
fn play_licence_status() -> Result<serde_json::Value, String> {
    Ok(serde_json::json!({
        "lifetime": false,
        "supportActive": false,
        "integrity": false,
        "trialStartedAt": serde_json::Value::Null
    }))
}

#[tauri::command]
fn play_purchase(_sku: String, _offer: Option<String>) -> Result<(), String> {
    Err("Purchases are only available in the Google Play build.".into())
}

#[tauri::command]
fn play_restore() -> Result<serde_json::Value, String> {
    play_licence_status()
}

#[tauri::command]
fn play_review() -> Result<serde_json::Value, String> {
    Ok(serde_json::json!({
        "launched": false,
        "fallback": true
    }))
}

#[tauri::command]
fn play_open_listing() -> Result<(), String> {
    Err("Google Play reviews are only available in the Android build.".into())
}

/// App-command path used when the plugin ACL blocks plugin:growurdhan|cas_*.
/// On Android this always calls the Chaquopy plugin handle.
#[tauri::command]
fn cas_health<R: Runtime>(app: AppHandle<R>) -> Result<serde_json::Value, String> {
    #[cfg(target_os = "android")]
    {
        let Some(bridge) = app.try_state::<AndroidBridge<R>>() else {
            return Ok(serde_json::json!({
                "ok": false,
                "engine": "PYTHON_CASPARSER",
                "error": "Android CAS plugin was not registered.",
                "logPath": "Download/GrowUrDhan-diagnostics.txt"
            }));
        };
        phone_log(&app, "cas_health forwarding to plugin");
        return match bridge
            .plugin
            .run_mobile_plugin::<serde_json::Value>("cas_health", serde_json::json!({}))
        {
            Ok(v) => {
                phone_log(&app, &format!("cas_health plugin ok snippet={}", snippet(&v)));
                Ok(v)
            }
            Err(e) => {
                phone_log(&app, &format!("cas_health plugin err {e}"));
                Ok(serde_json::json!({
                    "ok": false,
                    "engine": "PYTHON_CASPARSER",
                    "error": e.to_string(),
                    "logPath": "Download/GrowUrDhan-diagnostics.txt"
                }))
            }
        };
    }
    #[cfg(not(target_os = "android"))]
    {
        let _ = app;
        Ok(serde_json::json!({
            "ok": false,
            "engine": "PYTHON_CASPARSER",
            "error": "Engine is not running."
        }))
    }
}

#[tauri::command]
fn cas_parse<R: Runtime>(
    app: AppHandle<R>,
    pdf_base64: String,
    password: Option<String>,
    filename: Option<String>,
) -> Result<serde_json::Value, String> {
    #[cfg(target_os = "android")]
    {
        let Some(bridge) = app.try_state::<AndroidBridge<R>>() else {
            return Err("Android CAS plugin was not registered.".into());
        };
        let name = filename.clone().unwrap_or_else(|| "statement.pdf".into());
        let pass_len = password.as_ref().map(|p| p.len()).unwrap_or(0);
        phone_log(
            &app,
            &format!(
                "cas_parse forward file={name} pdfChars={} passwordChars={pass_len}",
                pdf_base64.len()
            ),
        );
        let payload = serde_json::json!({
            "pdfBase64": pdf_base64,
            "password": password.unwrap_or_default(),
            "filename": name,
        });
        return bridge
            .plugin
            .run_mobile_plugin::<serde_json::Value>("cas_parse", payload)
            .map_err(|e| {
                phone_log(&app, &format!("cas_parse plugin err {e}"));
                e.to_string()
            });
    }
    #[cfg(not(target_os = "android"))]
    {
        let _ = (app, pdf_base64, password, filename);
        Err("Engine is not running.".into())
    }
}

#[cfg(target_os = "android")]
fn snippet(v: &serde_json::Value) -> String {
    let ok = v.get("ok").map(|x| x.to_string()).unwrap_or_default();
    let err = v.get("error").and_then(|x| x.as_str()).unwrap_or("");
    let mut s = format!("ok={ok} error={err}");
    if s.len() > 400 {
        s.truncate(400);
    }
    s
}

fn base64_encode(bytes: &[u8]) -> String {
    use base64::Engine;
    base64::engine::general_purpose::STANDARD.encode(bytes)
}

fn base64_decode(s: &str) -> Result<Vec<u8>, String> {
    use base64::Engine;
    base64::engine::general_purpose::STANDARD
        .decode(s.trim())
        .map_err(|e| e.to_string())
}
