#!/bin/bash

if [ -z "${JAVA_HOME}" ]; then
    # Gentoo
    if which java-config > /dev/null 2>&1; then
        export JAVA_HOME="$(java-config --jre-home)"
    else
        export JAVA_HOME="/usr"
    fi
fi

die() {
        local localmsg="$1"
        echo "FATAL: ${localmsg}" >&2
        exit 1
}

#remote debug parameters
#export DEBUG_OPTS="-Xdebug -Xrunjdwp:transport=dt_socket,address=1044,server=y,suspend=y"

while [ -n "$1" ]; do
    v="${1#*=}"
    case "$1" in
        --config-path=*)
            export CONFIG="${v}"
            ;;
        --help|*)
                cat <<__EOF__
Usage: $0
        --config-path=path  - Path for -Daerobase.config.dir param - Default /tmp/db.properties
__EOF__
        exit 1
    esac
    shift
done

[ -z "${CONFIG}" ] && export CONFIG=/tmp/db.properties

${JAVA_HOME}/bin/java ${DEBUG_OPTS} \
        -Daerobase.config.dir=${CONFIG} \
        -cp "../lib/*" \
        org.jboss.aerogear.unifiedpush.DBMaintenance