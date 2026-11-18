#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${BUILD_JAVA_HOME:-${JAVA_HOME:?Set JAVA_HOME to Java 17}}"
export PATH="$JAVA_HOME/bin:$PATH"
export MAVEN_OPTS='-Xmx768m -XX:+UseSerialGC'
export TZ=Asia/Bahrain
maven=(bash ./mvnw -B -ntp)
ci_config="file:$PWD/ci/application.properties"
db_name=flight-ci-postgres

case "${1:-}" in
  build)
    java -version
    "${maven[@]}" --version
    # Fresh SCM checkouts intentionally do not contain local application settings.
    for module in . identity-service maintenance-service notification-service gateway-service; do
      cp "$module/src/main/resources/application-example.properties" "$module/src/main/resources/application.properties"
    done
    for module in common . identity-service maintenance-service notification-service gateway-service; do
      "${maven[@]}" -f "$module/pom.xml" -DskipTests clean install
    done
    ;;
  database)
    # Refuse to replace a container: an interrupted run must be cleaned up explicitly.
    if docker container inspect "$db_name" >/dev/null 2>&1; then
      echo 'CI database already exists; inspect the previous run before retrying.' >&2
      exit 1
    fi
    docker run -d --name "$db_name" --label flight.ci.disposable=true \
      -e POSTGRES_DB=ci_aviation -e POSTGRES_USER=ci -e POSTGRES_PASSWORD=ci-disposable-only \
      -p 55432:5432 --tmpfs /var/lib/postgresql:rw postgres:18.6
    ready=false
    for attempt in {1..60}; do
      if docker exec "$db_name" pg_isready -U ci -d ci_aviation >/dev/null 2>&1; then ready=true; break; fi
      sleep 1
    done
    "$ready" || { docker logs --tail 60 "$db_name"; exit 1; }
    docker exec -i "$db_name" psql -X -v ON_ERROR_STOP=1 -U ci -d ci_aviation < ci/database/schema.sql
    docker exec -i "$db_name" psql -X -v ON_ERROR_STOP=1 -U ci -d ci_aviation < ci/database/constraints.sql
    docker exec "$db_name" createdb -U ci airline_admin_test
    "${maven[@]}" -f integration-tests/pom.xml test -Dtest=CiFixtureSetup \
      "-Dspring.config.additional-location=$ci_config"
    ;;
  tests)
    export AIRLINE_ADMIN_TEST_DATABASE_URL=jdbc:postgresql://docker:55432/airline_admin_test
    export AIRLINE_ADMIN_TEST_DATABASE_USERNAME=ci
    export AIRLINE_ADMIN_TEST_DATABASE_PASSWORD=ci-disposable-only
    "${maven[@]}" -f pom.xml verify "-Dspring.config.additional-location=$ci_config"
    "${maven[@]}" -f identity-service/pom.xml verify "-Dspring.config.additional-location=$ci_config"
    # IdentityFixtures holds a static EntityManager. Keep only the active test
    # context cached so it cannot point at a different context's transaction.
    "${maven[@]}" -f integration-tests/pom.xml verify '-Dtest=*Test' \
      -Dspring.test.context.cache.maxSize=1 "-Dspring.config.additional-location=$ci_config"
    ;;
  cucumber)
    "${maven[@]}" -f integration-tests/pom.xml test -Dtest=runners.TestRunner \
      "-Dspring.config.additional-location=$ci_config" \
      '-Dcucumber.plugin=pretty,summary,json:target/cucumber.json,html:target/cucumber.html'
    ;;
  images)
    : "${IMAGE_TAG:?Set an immutable IMAGE_TAG}"
    mkdir -p target/ci-images
    for service in aviation identity maintenance notification gateway; do
      module="$service-service"
      if [ "$service" = aviation ]; then module=.; fi
      context="target/ci-images/$service"
      mkdir -p "$context"
      cp "$module"/target/*-app.jar "$context/app.jar"
      cp ci/Dockerfile "$context/Dockerfile"
      docker build --label "org.opencontainers.image.revision=${GIT_COMMIT:?}" \
        -t "sauds-flight-system-$service:$IMAGE_TAG" "$context"
      docker image inspect "sauds-flight-system-$service:$IMAGE_TAG" --format '{{.Id}}'
    done
    ;;
  cleanup)
    if docker container inspect "$db_name" >/dev/null 2>&1; then
      label=$(docker inspect --format '{{index .Config.Labels "flight.ci.disposable"}}' "$db_name")
      [ "$label" = true ] || { echo 'Refusing to remove a non-CI container' >&2; exit 1; }
      docker rm -f -v "$db_name"
    fi
    ;;
  *) echo 'Usage: bash ci/backend.sh build|database|tests|cucumber|images|cleanup' >&2; exit 2 ;;
esac
