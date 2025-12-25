@rem Gradle startup script for Windows
@echo off
set JAVA_EXE=java.exe
%JAVA_EXE% -classpath "%~dp0\gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
