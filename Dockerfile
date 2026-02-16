# Dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace/app

# Install Maven
RUN apk add --no-cache maven

# Copy pom.xml and download dependencies (cached layer)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code
COPY src src

# Build the application
RUN mvn package -DskipTests -B

# Extract the built JAR layers
RUN mkdir -p target/dependency && (cd target/dependency; jar -xf ../nabat-0.0.1-SNAPSHOT.jar)

FROM eclipse-temurin:21-jre-alpine
VOLUME /tmp
WORKDIR /app

# Copy the extracted layers from build stage (done as root)
ARG DEPENDENCY=/workspace/app/target/dependency
COPY --from=build ${DEPENDENCY}/BOOT-INF/lib /app/lib
COPY --from=build ${DEPENDENCY}/META-INF /app/META-INF
COPY --from=build ${DEPENDENCY}/BOOT-INF/classes /app

# Install wget for HEALTHCHECK
RUN apk add --no-cache wget

# Create non-root user and give ownership of /app
RUN addgroup -S spring && adduser -S spring -G spring \
  && chown -R spring:spring /app

USER spring:spring

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java","-cp","app:app/lib/*","org.example.nabat.NabatApplication"]
