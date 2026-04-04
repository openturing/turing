; --------------------------------------------------------------------------
; Viglet Turing ES - NSIS Installer Script
;
; Requires NSIS 3.x
; --------------------------------------------------------------------------

!include "MUI2.nsh"
!include "FileFunc.nsh"
!include "nsDialogs.nsh"
!include "LogicLib.nsh"
!include "Sections.nsh"

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
VIProductVersion "2026.2.4.0"
VIAddVersionKey "ProductName" "Viglet Turing ES"
VIAddVersionKey "FileVersion" "2026.2.4.0"
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
Var ExamplesAnySelected

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
!define MUI_COMPONENTSPAGE_SMALLDESC

; --------------------------------------------------------------------------
; Pages
; --------------------------------------------------------------------------
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_LICENSE "..\..\LICENSE"
!insertmacro MUI_PAGE_DIRECTORY
Page custom JavaPageCreate JavaPageLeave
!insertmacro MUI_PAGE_COMPONENTS
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

!insertmacro MUI_LANGUAGE "English"
!insertmacro MUI_LANGUAGE "Portuguese"

; --------------------------------------------------------------------------
; Component descriptions
; --------------------------------------------------------------------------
LangString DESC_SecMain       ${LANG_ENGLISH} "Turing ES search platform (required). Includes the server, CLI tools, and administration console."
LangString DESC_SecMain       ${LANG_PORTUGUESE} "Plataforma de busca Turing ES (obrigatorio). Inclui o servidor, ferramentas CLI e console de administracao."

LangString DESC_SecExamples   ${LANG_ENGLISH} "Sample search sites with SPA templates and demo content. Great for learning and testing."
LangString DESC_SecExamples   ${LANG_PORTUGUESE} "Sites de busca de exemplo com templates SPA e conteudo de demonstracao. Otimo para aprender e testar."

LangString DESC_SecMythical   ${LANG_ENGLISH} "The Bestiary - Encyclopedia of mythical creatures with multi-language search, faceted filters, danger ratings, and More Like This recommendations."
LangString DESC_SecMythical   ${LANG_PORTUGUESE} "O Bestiario - Enciclopedia de criaturas miticas com busca multi-idioma, filtros facetados, nivel de perigo e recomendacoes."

LangString DESC_SecSpace      ${LANG_ENGLISH} "Mission Control - Space missions search with agency filters, launch dates, crew data, and a futuristic HUD-style interface."
LangString DESC_SecSpace      ${LANG_PORTUGUESE} "Controle de Missao - Busca de missoes espaciais com filtros de agencia, datas de lancamento, dados de tripulacao e interface futurista."

LangString DESC_SecVinyl      ${LANG_ENGLISH} "The Crate - Vinyl records catalog with album art, artist search, genre facets, star ratings, and condition grading."
LangString DESC_SecVinyl      ${LANG_PORTUGUESE} "The Crate - Catalogo de discos de vinil com capas, busca por artista, facetas de genero, avaliacoes e classificacao de condicao."

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
; Installer Sections
; --------------------------------------------------------------------------

; ---- Core (always installed) ----
Section "Turing ES (required)" SecMain
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

    ; Create export dir for auto-import
    CreateDirectory "$INSTDIR\export"

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

; ---- Example Sites Group ----
SectionGroup "Example Sites" SecExamples

    Section "Mythical Creatures" SecMythical
        SetOutPath "$INSTDIR\export"
        File /nonfatal "${STAGE}\examples\mythical-creatures.zip"
        StrCpy $ExamplesAnySelected "1"
    SectionEnd

    Section "Space Missions" SecSpace
        SetOutPath "$INSTDIR\export"
        File /nonfatal "${STAGE}\examples\space-missions.zip"
        StrCpy $ExamplesAnySelected "1"
    SectionEnd

    Section "Vinyl Records" SecVinyl
        SetOutPath "$INSTDIR\export"
        File /nonfatal "${STAGE}\examples\vinyl-records.zip"
        StrCpy $ExamplesAnySelected "1"
    SectionEnd

SectionGroupEnd

; ---- Enable filesystem storage if any example was selected ----
Section "-ConfigureStorage"
    ${If} $ExamplesAnySelected == "1"
        ; Append storage config to properties file
        FileOpen $0 "$INSTDIR\server\viglet-turing.properties" a
        FileSeek $0 0 END
        FileWrite $0 "$\r$\n"
        FileWrite $0 "# Storage enabled for SPA page templates$\r$\n"
        FileWrite $0 "turing.storage.type=FILESYSTEM$\r$\n"
        FileWrite $0 "turing.storage.filesystem.path=./store/assets$\r$\n"
        FileClose $0
    ${EndIf}
SectionEnd

; --------------------------------------------------------------------------
; Section descriptions
; --------------------------------------------------------------------------
!insertmacro MUI_FUNCTION_DESCRIPTION_BEGIN
    !insertmacro MUI_DESCRIPTION_TEXT ${SecMain}     $(DESC_SecMain)
    !insertmacro MUI_DESCRIPTION_TEXT ${SecExamples}  $(DESC_SecExamples)
    !insertmacro MUI_DESCRIPTION_TEXT ${SecMythical}  $(DESC_SecMythical)
    !insertmacro MUI_DESCRIPTION_TEXT ${SecSpace}     $(DESC_SecSpace)
    !insertmacro MUI_DESCRIPTION_TEXT ${SecVinyl}     $(DESC_SecVinyl)
!insertmacro MUI_FUNCTION_DESCRIPTION_END

; --------------------------------------------------------------------------
; Uninstaller Section
; --------------------------------------------------------------------------
Section "Uninstall"
    ; Remove files
    RMDir /r "$INSTDIR\server"
    RMDir /r "$INSTDIR\utils"
    RMDir /r "$INSTDIR\bin"
    RMDir /r "$INSTDIR\export"
    RMDir /r "$INSTDIR\store"
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
