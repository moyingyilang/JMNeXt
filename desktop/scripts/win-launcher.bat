@rem SPDX-License-Identifier: MPL-2.0
@echo off
rem 免装 Java：用同目录下的 runtime，不依赖系统 PATH 里的 java
setlocal
set DIR=%~dp0
"%DIR%runtime\bin\java.exe" -Xmx2g -jar "%DIR%jmnext-windows-arm64.jar" %*
endlocal
