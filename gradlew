#!/bin/sh

#
# Copyright © 2015-2021 the original authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

if ! command -v "$JAVACMD" >/dev/null 2>&1 ; then
    echo "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH." >&2
    exit 1
fi

APP_BASE_NAME=`basename "$0"`
DIRNAME=`dirname "$0"`
POW_DIR=`cd "$DIRNAME" && pwd`

APP_HOME="$POW_DIR"

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        # IBM's JDK on AIX uses special locations for the Java executable
        JAVACMD="$JAVA_HOME/jre/sh/java"
    fi
fi

# Increase the maximum file descriptors if we can.
if [ "$cygwin" = "false" ] && [ "$darwin" = "false" ] && [ "$nonstop" = "false" ] ; then
    MAX_FD_LIMIT=`ulimit -H -n`
    if [ $MAX_FD_LIMIT -gt 0 ] ; then
        VALID_FD_LIMIT=`ulimit -n 1024`
        if [ $? -ne 0 ] ; then
            echo "Warning: maximum number of file descriptors is not high enough ($MAX_FD_LIMIT)" >&2
        fi
    fi
fi

# Escape an argument to safely pass to a JVM
save_args () {
    for i do
        printf '%s\n' "$i" | sed "s/'/'\\\\''/g;1s/^/'/;\\\$s/\$/'/"
    done
}

# Collect all arguments for the java command, prioritizing the shell escape
if [ -n "$JAVA_OPTS" ] ; then
    DEFAULT_JVM_OPTS="$DEFAULT_JVM_OPTS $JAVA_OPTS"
fi

JAVACMD_ARGS=`save_args "$@"`

# Execute JVM
eval "exec \"$JAVACMD\" $DEFAULT_JVM_OPTS -classpath \"$APP_HOME/gradle/wrapper/gradle-wrapper.jar\" org.gradle.wrapper.GradleWrapperMain $JAVACMD_ARGS"
