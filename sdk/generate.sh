#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
spec="backend/src/main/resources/openapi/app-api.yaml"
generator_image="openapitools/openapi-generator-cli:v7.4.0"

version="${SDK_VERSION:-$(sed -n 's/^  version: *//p' "$root/$spec" | head -n 1)}"
targets=("$@")
if [[ ${#targets[@]} -eq 0 || "${targets[0]}" == "all" ]]; then
    targets=(kotlin cpp)
fi

for target in "${targets[@]}"; do
    config="sdk/$target.yaml"
    if [[ ! -f "$root/$config" ]]; then
        echo "Unknown target '$target' (expected: kotlin, cpp, all)" >&2
        exit 1
    fi

    rm -rf "${root:?}/sdk/$target"
    docker run --rm --user "$(id -u):$(id -g)" -v "$root:/local" "$generator_image" generate \
        -i "/local/$spec" \
        -c "/local/$config" \
        -o "/local/sdk/$target" \
        -p "artifactVersion=$version" \
        -p "packageVersion=$version"
done
