#!/usr/bin/env bash

# Shared local-build helper. Source this file, then call configure_jdk17.
# It never installs Java automatically; it only selects an existing JDK 17.

java_major_version() {
    local java_home="$1"
    local version_line=""

    if [[ -z "$java_home" || ! -x "$java_home/bin/java" ]]; then
        return 1
    fi

    version_line="$("$java_home/bin/java" -version 2>&1 | head -n 1 || true)"
    if [[ "$version_line" =~ \"([0-9]+)(\.[0-9]+)* ]]; then
        echo "${BASH_REMATCH[1]}"
        return 0
    fi

    return 1
}

is_jdk17() {
    local java_home="$1"
    local major=""
    major="$(java_major_version "$java_home" 2>/dev/null || true)"
    [[ "$major" == "17" ]]
}

configure_jdk17() {
    local candidate=""
    local macos_java17=""
    local current_java=""
    local current_version=""

    if [[ "$(uname -s)" == "Darwin" && -x /usr/libexec/java_home ]]; then
        macos_java17="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
    fi

    for candidate in \
        "${TRAFFIC_MONITORING_JAVA_HOME:-}" \
        "${JAVA_HOME:-}" \
        "$macos_java17" \
        "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home" \
        "/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home" \
        "/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home" \
        "/Library/Java/JavaVirtualMachines/openjdk-17.jdk/Contents/Home" \
        "/usr/lib/jvm/temurin-17-jdk-amd64" \
        "/usr/lib/jvm/java-17-openjdk-amd64" \
        "/usr/lib/jvm/java-17-openjdk"; do
        if is_jdk17 "$candidate"; then
            export JAVA_HOME="$candidate"
            export PATH="$JAVA_HOME/bin:$PATH"
            return 0
        fi
    done

    current_java="$(command -v java 2>/dev/null || true)"
    if [[ -n "$current_java" ]]; then
        current_version="$(java -version 2>&1 | head -n 1 || true)"
    else
        current_version="not found"
    fi

    cat >&2 <<EOF
Error: JDK 17 is required to build Traffic Monitoring Android locally.

Current Java: ${current_version}
Gradle 8.9 cannot run this build on Java 25.

On macOS with Homebrew, install JDK 17 once:
  brew install openjdk@17

Then rerun the same repository command. The project scripts automatically detect
Homebrew's JDK 17, so you do not need to replace your system-wide Java 25.

Optional explicit override:
  export TRAFFIC_MONITORING_JAVA_HOME=/absolute/path/to/jdk-17/Contents/Home
EOF
    return 1
}
