# If you change this major version, change the --multi-release jdeps flag below
FROM openjdk:18-alpine AS build

RUN apk add \
      # Binutils provides objcopy binary which is used by --strip-debug jlink flag.
      binutils \
    ;
WORKDIR /app

# Cache the Gradle binary separately from the build.
COPY gradlew ./
COPY gradle/wrapper ./gradle/wrapper
RUN ./gradlew --version

COPY gradle ./gradle
COPY build.gradle settings.gradle ./
COPY src ./src
RUN ./gradlew assemble

# Find modules which are required.
RUN jdeps \
      --ignore-missing-deps \
      # Keep in sync with major version of container above.
      --multi-release 18 \
      --print-module-deps \
      --class-path build/install/ui-spy/lib/* \
    # Split comma-separated items into lines.
    | tr ',' '\n' \
    # Used only by kotlinx.coroutines debug agent which we do not use.
    | grep -v java.instrument \
    # Used only by kotlinx.coroutines debug agent which we do not use.
    | grep -v jdk.unsupported \
    # Add crypto module to the list so TLS works for HTTPS requests.
    | awk '{print $1 "\n" "jdk.crypto.ec"}' \
    # Join lines with comma.
    | tr '\n' ',' \
    # Replace trailing comma with a newline.
    | sed 's/,$/\n/' \
    # Print to stdout AND write to this file.
    | tee jdeps.txt \
  ;

# Build custom minimal JRE with only the modules we need.
RUN jlink \
      --verbose \
      --compress 2 \
      --strip-debug \
      --no-header-files \
      --no-man-pages \
      --output jre \
      --add-modules $(cat jdeps.txt) \
   ;

FROM alpine:3.17.3

COPY --from=build /app/jre /jre
ENV JAVA_HOME="/jre"

COPY --from=build /app/build/install/ui-spy/ /app

ENTRYPOINT ["/app/bin/ui-spy"]
CMD ["--data", "/data", "/config/config.toml"]
