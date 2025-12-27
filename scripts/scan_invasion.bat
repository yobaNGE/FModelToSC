@echo off
setlocal EnableDelayedExpansion

REM Folder to scan (change this path if needed)
set "SCANPATH=C:\Program Files\Fmodel\Output\Exports\SquadGame\Plugins\Mods\Steel_Division\Content\Maps"

REM Output file is created next to this .bat file
set "OUTPUT=%~dp0Invasion_Layers_with_FactionsList.txt"


echo Scanning folder:
echo   %SCANPATH%
echo Saving results to:
echo   %OUTPUT%
echo.

REM Scan filesystem
for /R "%SCANPATH%" %%F in (*Invasion*.*) do (
    set "name=%%~nxF"
    set "skip="

    REM Skip files with _N or _n in the name
    echo !name! | find /I "_N" >nul && set "skip=1"
    echo !name! | find /I "_n" >nul && set "skip=1"

    if not defined skip (
        REM Look for "Type": "BP_SQLayer_C"
        findstr /C:"\"Type\": \"BP_SQLayer_C\"" "%%F" >nul
        if not errorlevel 1 (
            REM Look for FactionsList
            findstr /I "FactionsList" "%%F" >nul
            if not errorlevel 1 (
                echo %%F>> "%OUTPUT%"
            )
        )
    )
)

echo Done.
echo Results saved to: %OUTPUT%
echo.
pause

endlocal
