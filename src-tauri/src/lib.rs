use std::fs;
use std::path::PathBuf;
use tauri::plugin::TauriPlugin;
use tauri::{AppHandle, Manager, Runtime};

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
            play_open_listing
        ])
        .run(tauri::generate_context!())
        .expect("GrowUrDhan failed to start");
}

fn growurdhan_plugin<R: Runtime>() -> TauriPlugin<R> {
    tauri::plugin::Builder::new("growurdhan")
        .setup(|_app, api| {
            #[cfg(target_os = "android")]
            {
                api.register_android_plugin("in.growurdhan.app", "GrowUrDhanPlugin")?;
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

/// Sandboxed file store. On Android the overlay plugin wraps this with
/// EncryptedFile + Keystore when it is registered; JS still AES-GCM wraps first.
#[tauri::command]
fn vault_blob_load(app: AppHandle) -> Result<Option<String>, String> {
    let path = vault_path(&app)?;
    if !path.exists() {
        return Ok(None);
    }
    let bytes = fs::read(path).map_err(|e| e.to_string())?;
    Ok(Some(base64_encode(&bytes)))
}

#[tauri::command]
fn vault_blob_save(app: AppHandle, bytes_b64: String) -> Result<(), String> {
    let path = vault_path(&app)?;
    let bytes = base64_decode(&bytes_b64)?;
    fs::write(path, bytes).map_err(|e| e.to_string())
}

/// Without the Android Play plugin this must not grant a trial.
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
