#!/bin/sh
set -eu
owner=$1
work=$2
pending=$3
failed=$4
finish() {
    status=$?
    if [ "$status" -ne 0 ]; then printf '%s\n' "$status" > "$failed"; fi
    rm -f -- "$pending"
    rm -rf -- "$work"
}
trap finish EXIT
trap 'exit 1' HUP INT TERM
: > "$work/ready"
tries=0
while kill -0 "$owner" 2>/dev/null; do
    tries=$((tries + 1))
    [ "$tries" -lt 60 ] || exit 1
    sleep 1
done
[ -f "$pending" ] || exit 0
status=0
while IFS= read -r path; do
    [ -n "$path" ] || continue
    rm -rf -- "$path" || status=1
done < "$work/roots.txt"
exit "$status"
