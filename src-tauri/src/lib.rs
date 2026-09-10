use std::process::{Child, Command, Stdio};
use std::sync::Mutex;
use tauri::{Manager, RunEvent};

struct Engine(Mutex<Option<Child>>);

fn engine_cmd(app: &tauri::AppHandle) -> Option<Command> {
    let resource = app.path().resource_dir().ok()?;
    let exe = if cfg!(windows) {
        resource.join("gud-engine.exe")
    } else {
        resource.join("gud-engine")
    };
    if exe.exists() {
        let mut cmd = Command::new(exe);
        cmd.stdin(Stdio::null()).stdout(Stdio::null()).stderr(Stdio::null());
        return Some(cmd);
    }
    None
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

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_fs::init())
        .manage(Engine(Mutex::new(None)))
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
