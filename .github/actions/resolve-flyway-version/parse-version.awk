/^org\.flywaydb:flyway-core:[^[:space:]]+/ {
    version = $1
    if ($(NF - 1) == "->") {
        version = $NF
    }
    sub(/^org\.flywaydb:flyway-core:/, "", version)
    if (version ~ /^[0-9]+\.[0-9]+\.[0-9]+([-.][0-9A-Za-z.-]+)?$/) {
        print version
        exit
    }
}
