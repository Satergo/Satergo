#!/usr/bin/env sh

# Resolve links: $0 may be a link
PRG="$0"
# Need this for relative symlinks.
while [ -h "$PRG" ] ; do
    ls=`ls -ld "$PRG"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=`dirname "$PRG"`"/$link"
    fi
done
# Make sure that the current working directory is the root of the program.
cd "`dirname \"$PRG\"`/.." || exit
APP_HOME="`pwd -P`"

# Add default JVM options here. You can also use JAVA_OPTS and SATERGO_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS='{DEFAULT_JVM_OPTS}'

CLASSPATH="$APP_HOME/lib/*"

# Escape application args
save () {
    for i do printf %s\\n "$i" | sed "s/'/'\\\\''/g;1s/^/'/;\$s/\$/' \\\\/" ; done
    echo " "
}
APP_ARGS=$(save "$@")

# Collect all arguments for the java command, following the shell quoting and substitution rules.
eval set -- $DEFAULT_JVM_OPTS $JAVA_OPTS $SATERGO_OPTS -classpath "\"$CLASSPATH\"" {MAIN_CLASS} "$APP_ARGS"

exec "$APP_HOME/bin/java" "$@"
