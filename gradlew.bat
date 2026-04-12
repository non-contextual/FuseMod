@rem ##########################################################################
@rem  Gradle startup script for Windows
@rem
@rem  优先使用 gradle/wrapper/gradle-wrapper.jar（完整 wrapper），
@rem  如果 JAR 不存在，则回退到系统全局 gradle 命令。
@rem
@rem  获取 gradle-wrapper.jar 的方法：
@rem    从 https://github.com/FabricMC/fabric-example-mod 复制
@rem    gradle/wrapper/gradle-wrapper.jar 到本项目同路径。
@rem  或者直接用全局 gradle：
@rem    gradle build  （效果等同 gradlew build）
@rem ##########################################################################
@if "%DEBUG%"=="" @echo off
@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if "%ERRORLEVEL%"=="0" goto execute

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo.
goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
goto fail

:execute
@rem Check for gradle-wrapper.jar
if exist "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" goto runWrapper

@rem 没有 wrapper JAR，回退到全局 gradle
echo [WARNING] gradle-wrapper.jar not found. Falling back to system gradle.
echo [INFO] To use the proper wrapper, copy gradle-wrapper.jar from:
echo [INFO]   https://github.com/FabricMC/fabric-example-mod/tree/1.21/gradle/wrapper
echo.
gradle %*
goto end

:runWrapper
"%JAVA_EXE%" -classpath "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" ^
  org.gradle.wrapper.GradleWrapperMain %*

:end
if "%ERRORLEVEL%"=="0" goto mainEnd

:fail
exit /b 1

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
