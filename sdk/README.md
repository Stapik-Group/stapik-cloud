# Client SDKs

Client libraries for the application API, generated from `backend/src/main/resources/openapi/app-api.yaml` with OpenAPI Generator (same version as the backend build, run through Docker).

```bash
sdk/generate.sh          # both
sdk/generate.sh kotlin   # sdk/kotlin: Gradle project for Android / JVM (OkHttp 4, Moshi)
sdk/generate.sh cpp      # sdk/cpp: CMake project for desktop apps (C++ REST SDK)
```

The SDK version follows `info.version` of the spec unless `SDK_VERSION` is set; releases set it to the tag version and attach both SDKs as zip files. Generated code is not committed. Generator options live in `kotlin.yaml` and `cpp.yaml`.

The spec declares the server as the relative path `/api/v1`, so set the full base URL on the client (the `basePath` constructor argument of the Kotlin API classes, `ApiConfiguration::setBaseUrl` in C++) and pass the key as the `x-api-key` header.

The C++ client needs the [C++ REST SDK](https://github.com/microsoft/cpprestsdk), Boost and OpenSSL; it is in maintenance mode upstream, which is why it is the only non-Qt C++ client generator.
