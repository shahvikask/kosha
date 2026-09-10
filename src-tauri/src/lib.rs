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
            cas_prices
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
