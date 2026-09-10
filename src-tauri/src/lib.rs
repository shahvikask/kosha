mod vault;

use std::process::{Child, Command, Stdio};
use std::sync::Mutex;
use tauri::{Manager, RunEvent, State};

struct Engine(Mutex<Option<Child>>);

fn engine_cmd(app: &tauri::AppHandle) -> Option<Command> {
    let resource = app.path().resource_dir().ok()?;
    let exe = if cfg!(windows) {
        resource.join("gud-engine.exe")
    } else {
        resource.join("gud-engine")
    };
    if !exe.exists() {
        // Installed next to the exe as resources/gud-engine.exe
        if let Ok(dir) = app.path().resource_dir() {
            let alt = dir.join("resources").join(if cfg!(windows) { "gud-engine.exe" } else { "gud-engine" });
            if alt.exists() {
                let mut cmd = Command::new(alt);
                cmd.stdin(Stdio::null()).stdout(Stdio::null()).stderr(Stdio::null());
                return Some(cmd);
            }
        }
        return None;
    }
    let mut cmd = Command::new(exe);
    cmd.stdin(Stdio::null()).stdout(Stdio::null()).stderr(Stdio::null());
    Some(cmd)
}

fn spawn_engine(app: &tauri::AppHandle) {
    if cfg!(debug_assertions) {
        return;
    }
    if let Some(mut cmd) = engine_cmd(app) {
        match cmd.spawn() {
            Ok(child) => {
                if let Some(state) = app.try_state::<Engine>() {
                    *state.0.lock().unwrap() = Some(child);
                }
            }
            Err(e) => eprintln!("GrowUrDhan engine failed to start: {e}"),
        }
    }
}

fn kill_engine(app: &tauri::AppHandle) {
    if let Some(state) = app.try_state::<Engine>() {
        if let Some(mut child) = state.0.lock().unwrap().take() {
            let _ = child.kill();
        }
    }
}

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

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .manage(Engine(Mutex::new(None)))
        .manage(vault::Vault::new())
        .invoke_handler(tauri::generate_handler![sql_open, sql_exec, sql_query, sql_close])
        .setup(|app| {
            spawn_engine(app.handle());
            Ok(())
        })
        .build(tauri::generate_context!())
        .expect("GrowUrDhan failed to start")
        .run(|app, event| {
            if let RunEvent::Exit = event {
                kill_engine(app);
            }
        });
}
