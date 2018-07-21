@ECHO OFF
IF %1.==. GOTO No1
REM Remove trailing /
set CONFIG=%1

REM set debug parameters
REM set DEBUG_OPTS="-Xdebug -Xrunjdwp:transport=dt_socket,address=1044,server=y,suspend=y"

java %DEBUG_OPTS% ^
  -cp "..\lib\*" ^
  "-Daerobase.config.dir=%CONFIG%" ^
  org.jboss.aerogear.unifiedpush.DBMaintenance

GOTO End1

:No1
  ECHO Missing Config file
GOTO End1

:End1
@ECHO ON
