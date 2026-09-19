mod engine;
mod vault;

use tauri::{Manager, RunEvent, State};

#[tauri::command]
fn sql_open(vault: State<vault::Vault>) -> Result<String, String> {
    vault::open(&vault)
}

#[tauri::command]
fn sql_exec(vault: State<vault::Vault>, sql: String, params: Vec<serde_json::Value>) -> Result<(), String> {
    vault::exec(&vault, &sql, &params)
}

#[tauri::command]
fn sql_query(
    vault: State<vault::Vault>,
    sql: String,
    params: Vec<serde_json::Value>,
) -> Result<Vec<serde_json::Map<String, serde_json::Value>>, String> {
    vault::query(&vault, &sql, &params)
}

#[tauri::command]
fn sql_close(vault: State<vault::Vault>) -> Result<(), String> {
    vault::close(&vault)
}

#[tauri::command]
fn cas_health(app: tauri::AppHandle, eng: State<engine::Engine>) -> bool {
    if engine::sidecar_up() {
        return true;
    }
    let _ = engine::spawn(&app, &eng);
    engine::sidecar_up()
}

#[tauri::command]
fn engine_ensure(app: tauri::AppHandle, eng: State<engine::Engine>) -> Result<(), String> {
    engine::ensure(&app, &eng)
}

#[tauri::command]
fn cas_parse(
    app: tauri::AppHandle,
    eng: State<engine::Engine>,
    filename: String,
    password: String,
    pdf_base64: String,
) -> Result<serde_json::Value, String> {
    engine::ensure(&app, &eng)?;
    engine::parse(serde_json::json!({
        "filename": filename,
        "password": password,
        "pdfBase64": pdf_base64,
    }))
}

#[tauri::command]
fn cas_prices(app: tauri::AppHandle, eng: State<engine::Engine>) -> Result<serde_json::Value, String> {
    engine::ensure(&app, &eng)?;
    engine::prices()
}

/// Async on purpose: a sync command runs on the UI thread, and a blocking
/// Windows file dialog on that thread never appears (or deadlocks).
#[tauri::command]
async fn backup_save(filename: String, bytes_b64: String) -> Result<bool, String> {
    use base64::Engine as _;
    let bytes = base64::engine::general_purpose::STANDARD
        .decode(bytes_b64)
        .map_err(|e| e.to_string())?;
    let path = rfd::FileDialog::new()
        .set_file_name(&filename)
        .add_filter("GrowUrDhan backup", &["kosha"])
        .save_file();
    let Some(path) = path else {
        return Ok(false);
    };
    std::fs::write(path, bytes).map_err(|e| e.to_string())?;
    Ok(true)
}

#[tauri::command]
async fn backup_open() -> Result<Option<String>, String> {
    use base64::Engine as _;
    let path = rfd::FileDialog::new()
        .add_filter("GrowUrDhan backup", &["kosha", "json"])
        .pick_file();
    let Some(path) = path else {
        return Ok(None);
    };
    let bytes = std::fs::read(path).map_err(|e| e.to_string())?;
    Ok(Some(base64::engine::general_purpose::STANDARD.encode(bytes)))
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .manage(engine::Engine::new())
        .manage(vault::Vault::new())
        .invoke_handler(tauri::generate_handler![
            sql_open,
            sql_exec,
            sql_query,
            sql_close,
            cas_health,
            engine_ensure,
            cas_parse,
            cas_prices,
            backup_save,
            backup_open
        ])
        .setup(|app| {
            let handle = app.handle().clone();
            let eng = app.state::<engine::Engine>();
            let _ = engine::spawn(&handle, &*eng);
            Ok(())
        })
        .build(tauri::generate_context!())
        .expect("GrowUrDhan failed to start")
        .run(|app, event| {
            if let RunEvent::Exit = event {
                engine::kill(&*app.state::<engine::Engine>());
            }
        });
}
