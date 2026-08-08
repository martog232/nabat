# Dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace/app

# The Maven Wrapper, not `apk add maven`. Two reasons: the image then builds with the same
# Maven version as local and CI builds instead of whatever Alpine currently ships, and it
# removes an unpinnable apk package. hadolint's DL3018 asks for `apk add pkg=version`, but
# pinning Alpine versions is a trap — they are removed from the repositories as packages
# update, so a pinned build stops working with no change on our side. Not installing the
# package at all is the better answer.
COPY .mvn .mvn
COPY mvnw mvnw
COPY pom.xml .
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src src

# Build the application
RUN ./mvnw package -DskipTests -B

# Extract the built JAR layers.
#
# WORKDIR rather than `(cd target/dependency; ...)`: a subshell cd that fails carries on
# regardless, extracting into the wrong directory (DL3003, SC2164). The jar is also matched
# by glob now — the filename used to be written out in full, so a version bump in pom.xml
# broke the image build with a confusing "no such file" from jar.
WORKDIR /workspace/app/target/dependency
RUN jar -xf ../nabat-*.jar

FROM eclipse-temurin:21-jre-alpine
VOLUME /tmp
WORKDIR /app

# Copy the extracted layers from build stage (done as root)
ARG DEPENDENCY=/workspace/app/target/dependency
COPY --from=build ${DEPENDENCY}/BOOT-INF/lib /app/lib
COPY --from=build ${DEPENDENCY}/META-INF /app/META-INF
COPY --from=build ${DEPENDENCY}/BOOT-INF/classes /app

# Explicit numeric uid and gid, and USER given numerically.
#
# Kubernetes cannot verify `runAsNonRoot` against a username — it has no way to resolve
# `spring` to an id — so a pod with that security context refuses to start (hadolint DL3066
# flags the same thing). 1001 rather than 1000 to avoid colliding with the default first
# user on most base images.
RUN addgroup -S -g 1001 spring \
  && adduser -S -u 1001 -G spring spring \
  && mkdir -p /app/uploads \
  && chown -R 1001:1001 /app

USER 1001:1001

# BusyBox already provides wget in this base image, so nothing is installed for the health
# check. Its flags differ from GNU wget: `-q` rather than `--no-verbose`, and it has no
# `--tries`. The previous command used GNU-only flags, which is why a wget package had to be
# added at all.
# Exec form, so no shell is started per probe (DL3025). `|| exit 1` is gone with it and was
# never needed: wget already exits non-zero when the request fails, which is exactly what
# HEALTHCHECK reads.
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD ["wget", "-q", "--spider", "http://localhost:8080/actuator/health"]

EXPOSE 8080

ENTRYPOINT ["java","-cp","/app:/app/lib/*","org.example.nabat.NabatApplication"]
