; --------------------------------------------------------------------------
; Viglet Turing ES - NSIS Installer Script
;
; Requires NSIS 3.x
; --------------------------------------------------------------------------

!include "MUI2.nsh"
!include "FileFunc.nsh"
!include "nsDialogs.nsh"
!include "LogicLib.nsh"

; --------------------------------------------------------------------------
; General
; --------------------------------------------------------------------------
Name "Viglet Turing ES"
!ifndef OUTDIR
  !define OUTDIR "..\..\target"
!endif
OutFile "${OUTDIR}\turing-install.exe"
InstallDir "$PROGRAMFILES64\Viglet\Turing"
InstallDirRegKey HKLM "Software\Viglet\Turing" "InstallDir"
RequestExecutionLevel admin
SetCompressor /SOLID lzma

; --------------------------------------------------------------------------
; Version info
; --------------------------------------------------------------------------
VIProductVersion "1.0.0.0"
VIAddVersionKey "ProductName" "Viglet Turing ES"
VIAddVersionKey "CompanyName" "Viglet"
VIAddVersionKey "FileDescription" "Viglet Turing ES Installer"
VIAddVersionKey "LegalCopyright" "Apache License 2.0"

; --------------------------------------------------------------------------
; Variables
; --------------------------------------------------------------------------
Var JavaHome
Var JavaDialog
Var JavaDirText
Var JavaDirBrowse
Var JavaStatusLabel

; --------------------------------------------------------------------------
; MUI Settings
; --------------------------------------------------------------------------
!define MUI_ICON "turing.ico"
!define MUI_UNICON "turing.ico"
!define MUI_WELCOMEFINISHPAGE_BITMAP "wizard-sidebar.bmp"
!define MUI_UNWELCOMEFINISHPAGE_BITMAP "wizard-sidebar.bmp"
!define MUI_HEADERIMAGE
!define MUI_HEADERIMAGE_BITMAP "wizard-header.bmp"
!define MUI_HEADERIMAGE_RIGHT
!define MUI_ABORTWARNING

; --------------------------------------------------------------------------
; Pages
; --------------------------------------------------------------------------
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_LICENSE "..\..\LICENSE"
!insertmacro MUI_PAGE_DIRECTORY
Page custom JavaPageCreate JavaPageLeave
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

!insertmacro MUI_LANGUAGE "English"
!insertmacro MUI_LANGUAGE "Portuguese"

; --------------------------------------------------------------------------
; Java Page
; --------------------------------------------------------------------------
Function JavaPageCreate
    !insertmacro MUI_HEADER_TEXT "Java Configuration" "Select the Java 21+ installation directory."

    nsDialogs::Create 1018
    Pop $JavaDialog
    ${If} $JavaDialog == error
        Abort
    ${EndIf}

    StrCpy $JavaHome ""

    ; Auto-detect: JAVA_HOME, then registry
    ReadEnvStr $0 "JAVA_HOME"
    ${If} $0 != ""
    ${AndIf} ${FileExists} "$0\bin\java.exe"
        StrCpy $JavaHome "$0"
    ${EndIf}

    ${If} $JavaHome == ""
        ReadRegStr $0 HKLM "SOFTWARE\Eclipse Adoptium\JDK\21" "Path"
        ${If} $0 != ""
        ${AndIf} ${FileExists} "$0\bin\java.exe"
            StrCpy $JavaHome "$0"
        ${EndIf}
    ${EndIf}

    ${If} $JavaHome == ""
        ReadRegStr $0 HKLM "SOFTWARE\Eclipse Foundation\JDK\21" "Path"
        ${If} $0 != ""
        ${AndIf} ${FileExists} "$0\bin\java.exe"
            StrCpy $JavaHome "$0"
        ${EndIf}
    ${EndIf}

    ${If} $JavaHome == ""
        ReadRegStr $0 HKLM "SOFTWARE\JavaSoft\Java Development Kit\21" "JavaHome"
        ${If} $0 != ""
        ${AndIf} ${FileExists} "$0\bin\java.exe"
            StrCpy $JavaHome "$0"
        ${EndIf}
    ${EndIf}

    ${NSD_CreateLabel} 0 0 100% 36u \
        "Turing ES requires Java 21 or later. Select the Java installation directory (the folder containing the 'bin' subdirectory with java.exe)."
    Pop $0

    ${NSD_CreateLabel} 0 44u 100% 12u "Java Home directory:"
    Pop $0

    ${NSD_CreateDirRequest} 0 58u 80% 12u "$JavaHome"
    Pop $JavaDirText

    ${NSD_CreateBrowseButton} 82% 57u 18% 14u "Browse..."
    Pop $JavaDirBrowse
    ${NSD_OnClick} $JavaDirBrowse JavaBrowse

    ${NSD_CreateLabel} 0 78u 100% 12u ""
    Pop $JavaStatusLabel

    ${If} $JavaHome != ""
        ${NSD_SetText} $JavaStatusLabel "Auto-detected: $JavaHome"
    ${Else}
        ${NSD_SetText} $JavaStatusLabel "Java not detected. Please browse to your Java installation."
    ${EndIf}

    nsDialogs::Show
FunctionEnd

Function JavaBrowse
    nsDialogs::SelectFolderDialog "Select Java Home directory" "$JavaHome"
    Pop $0
    ${If} $0 != error
        StrCpy $JavaHome "$0"
        ${NSD_SetText} $JavaDirText "$JavaHome"
        ${If} ${FileExists} "$JavaHome\bin\java.exe"
            ${NSD_SetText} $JavaStatusLabel "Found: $JavaHome\bin\java.exe"
        ${Else}
            ${NSD_SetText} $JavaStatusLabel "Warning: java.exe not found in $JavaHome\bin\"
        ${EndIf}
    ${EndIf}
FunctionEnd

Function JavaPageLeave
    ${NSD_GetText} $JavaDirText $JavaHome

    ${IfNot} ${FileExists} "$JavaHome\bin\java.exe"
        MessageBox MB_YESNO|MB_ICONEXCLAMATION \
            "java.exe was not found in:$\n$JavaHome\bin\$\n$\nTuring ES requires Java 21+. Continue anyway?" \
            IDYES +2
        Abort
    ${EndIf}
FunctionEnd

; --------------------------------------------------------------------------
; Installer Section
; --------------------------------------------------------------------------
Section "Turing ES" SecMain
    SectionIn RO

    SetOutPath "$INSTDIR"
    File "${STAGE}\README.txt"
    File "turing.ico"

    ; Server
    SetOutPath "$INSTDIR\server"
    File "${STAGE}\server\viglet-turing.jar"
    File "${STAGE}\server\viglet-turing.properties"

    ; Utils (Solr configs etc.)
    SetOutPath "$INSTDIR\utils"
    File /nonfatal /r "${STAGE}\utils\*.*"

    ; Bin scripts
    SetOutPath "$INSTDIR\bin"
    File "${STAGE}\bin\turing.sh"
    File "${STAGE}\bin\turing.bat"

    ; Write java-home.conf
    FileOpen $0 "$INSTDIR\bin\java-home.conf" w
    FileWrite $0 "$JavaHome"
    FileClose $0

    ; Registry
    WriteRegStr HKLM "Software\Viglet\Turing" "InstallDir" "$INSTDIR"
    WriteRegStr HKLM "Software\Viglet\Turing" "JavaHome" "$JavaHome"

    ; Uninstaller
    WriteUninstaller "$INSTDIR\uninstall.exe"

    ; Add/Remove Programs
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\VigletTuring" \
        "DisplayName" "Viglet Turing ES"
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\VigletTuring" \
        "UninstallString" '"$INSTDIR\uninstall.exe"'
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\VigletTuring" \
        "InstallLocation" "$INSTDIR"
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\VigletTuring" \
        "Publisher" "Viglet"
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\VigletTuring" \
        "DisplayIcon" '"$INSTDIR\turing.ico"'
    WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\VigletTuring" \
        "NoModify" 1
    WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\VigletTuring" \
        "NoRepair" 1

    ${GetSize} "$INSTDIR" "/S=0K" $0 $1 $2
    IntFmt $0 "0x%08X" $0
    WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\VigletTuring" \
        "EstimatedSize" "$0"

    ; Start Menu shortcuts
    CreateDirectory "$SMPROGRAMS\Viglet Turing"
    CreateShortcut "$SMPROGRAMS\Viglet Turing\Turing ES.lnk" \
        "$INSTDIR\bin\turing.bat" "" "$INSTDIR\turing.ico" "" SW_SHOWMINIMIZED
    CreateShortcut "$SMPROGRAMS\Viglet Turing\Uninstall.lnk" \
        "$INSTDIR\uninstall.exe"

SectionEnd

; --------------------------------------------------------------------------
; Uninstaller Section
; --------------------------------------------------------------------------
Section "Uninstall"
    ; Remove files
    RMDir /r "$INSTDIR\server"
    RMDir /r "$INSTDIR\utils"
    RMDir /r "$INSTDIR\bin"
    Delete "$INSTDIR\README.txt"
    Delete "$INSTDIR\turing.ico"
    Delete "$INSTDIR\uninstall.exe"
    RMDir "$INSTDIR"

    ; Start Menu
    RMDir /r "$SMPROGRAMS\Viglet Turing"

    ; Registry
    DeleteRegKey HKLM "Software\Viglet\Turing"
    DeleteRegKey HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\VigletTuring"
SectionEnd
