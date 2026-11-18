FROM eclipse-temurin:17-jdk-jammy AS buildjdk
FROM docker:29.0.1-cli AS dockercli
FROM jenkins/inbound-agent:jdk21

USER root
COPY --from=buildjdk /opt/java/openjdk /opt/java/build-jdk17
COPY --from=dockercli /usr/local/bin/docker /usr/local/bin/docker
COPY --from=dockercli /usr/local/libexec/docker/cli-plugins /usr/local/libexec/docker/cli-plugins
USER jenkins
ENV BUILD_JAVA_HOME=/opt/java/build-jdk17
