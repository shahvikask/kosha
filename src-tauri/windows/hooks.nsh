; Kill the app and sidecar before overwrite so upgrades are not locked.
!macro NSIS_HOOK_PREINSTALL
  nsExec::ExecToLog 'taskkill /F /IM growurdhan.exe /T'
  nsExec::ExecToLog 'taskkill /F /IM GrowUrDhan.exe /T'
  nsExec::ExecToLog 'taskkill /F /IM gud-engine.exe /T'
  Sleep 1500
!macroend

!macro NSIS_HOOK_PREUNINSTALL
  nsExec::ExecToLog 'taskkill /F /IM growurdhan.exe /T'
  nsExec::ExecToLog 'taskkill /F /IM GrowUrDhan.exe /T'
  nsExec::ExecToLog 'taskkill /F /IM gud-engine.exe /T'
  Sleep 1000
!macroend
