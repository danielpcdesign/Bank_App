# syntax=docker/dockerfile:1

# ============================================================================
#  Stage 1 - build
# ============================================================================
# A full JDK, plus Maven via the wrapper. None of this ships. That is the entire point of a
# multi-stage build: the tools needed to PRODUCE an artifact are not the tools needed to RUN
# it, and shipping a compiler to production is both 30 MB of waste and extra attack surface.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build

# Copy the build definition BEFORE the source, and resolve dependencies in between.
#
# Docker caches each instruction as a layer and invalidates every layer after the first one
# whose inputs changed. Dependencies change when pom.xml changes - rarely. Source changes
# constantly. Copying everything at once would fuse them into one layer, so every edit to a
# controller would re-download the whole Spring dependency tree.
#
# Split like this, editing a .java file reuses the cached dependency layer and the build
# starts at `package`.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# chmod for the same reason CI needs it: git records the executable bit, Windows cannot
# supply it, and this file is committed 100644. See AGENTS.md section 7.
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q -DskipTests package

# Split the fat jar into its four layers, in change-frequency order:
#   dependencies -> spring-boot-loader -> snapshot-dependencies -> application
#
# A Spring Boot jar is ~35 MB, of which ~34 MB is third-party libraries that change when the
# pom changes and not otherwise. Copied as one file it is one 35 MB layer, rebuilt and
# re-pushed on every commit. Split, a code change rewrites only the ~200 KB `application`
# layer and the rest is reused from the registry.
#
# NOTE the flag: Boot 4 uses -Djarmode=tools. Boot 3 used -Djarmode=layertools, which is
# still what most tutorials show. Verified against this jar - `tools` offers `extract` and
# `list-layers`, and `list-layers` prints exactly the four names above.
RUN java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination extracted

# ============================================================================
#  Stage 2 - runtime
# ============================================================================
# JRE, not JDK: 111 MB against 143 MB, and nothing here needs to compile anything.
#
# Not -alpine (72 MB), deliberately. Alpine uses musl rather than glibc, and while Spring
# Boot and the Mongo driver run fine on it, that is one more variable in a project that has
# already spent time on Windows/Linux differences. Worth revisiting once the image is proven.
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# Containers run as root by default. A process that never needs to write outside its own
# directory should not have the authority to - if the application is compromised, the blast
# radius stops at this user. Costs one line.
RUN groupadd --system spring && useradd --system --gid spring --uid 1001 spring

# Order matters and mirrors the split above: least-frequently-changed first, so the layers
# most likely to be cache hits sit lowest in the stack.
COPY --from=build --chown=spring:spring /build/extracted/dependencies/ ./
COPY --from=build --chown=spring:spring /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=spring:spring /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=spring:spring /build/extracted/application/ ./

USER spring

# Documentation, not a firewall rule. EXPOSE records the port the process listens on; it
# does not publish it. `docker run -p 8080:8080` is what actually maps it.
EXPOSE 8080

# The extracted layout is not a jar, so `java -jar` has nothing to point at. The launcher
# class comes from the original jar's manifest - verified, not assumed:
#     Main-Class:  org.springframework.boot.loader.launch.JarLauncher
#     Start-Class: com.bank.BankapiApplication
# Boot 3's launcher was org.springframework.boot.loader.JarLauncher, without `.launch`.
#
# Exec form (JSON array), not shell form. Shell form wraps the process in /bin/sh, which
# swallows SIGTERM - so `docker stop` waits its full timeout and then kills the JVM, giving
# Spring no chance to close the Mongo connection pool.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

# No MONGODB_URI here, and no ENV for it either. It is a secret; it arrives at run time from
# the environment, and in Phase 11 from AWS Systems Manager Parameter Store. Anything written
# into a Dockerfile is readable in the image's history by anyone who can pull it.
