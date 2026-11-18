# Backend CI with Jenkins

`Jenkinsfile` belongs to the backend repository. Jenkins polls its configured Git
branch every five minutes, builds the shared library and five Java 17 services,
runs Aviation, Identity and integration tests (including concurrency), explicitly runs the
14-scenario Cucumber runner, and builds five Docker images from the tested JARs.
Every Maven and Docker command must succeed. Reports and application JARs are
archived even after failures. Image tags are `<12-character-commit>-<build-number>`.

The initial pipeline is CI. Registry publication and EC2 deployment require AWS
authentication and will be connected separately; a green CI build does not mean
that the application was deployed.

## Jenkins installation and agent

The existing controller is `jenkins-local`, with data in Docker volume
`jenkinsvol1` and its UI at http://localhost:8082/. Start it with
`docker start jenkins-local`. Its Java 21 runtime is separate from Java 17 builds.

1. Build the agent: `docker compose -f ci/jenkins/compose.yml build agent`.
2. Sign in to Jenkins. Run `ci/jenkins/register-agent.groovy` in Manage Jenkins >
   Script Console. This creates only the dedicated `flight-backend-agent` node
   and stages its connection secret privately inside the controller.
3. Run `python ci/jenkins/bootstrap-agent.py`. It transfers the secret directly
   between containers without printing it or writing it to the host filesystem.
4. Verify the node is online. It has one executor and the `flight-backend` label.
5. Create a Pipeline job using Pipeline script from SCM, Git, this repository,
   the chosen branch, and Script Path `Jenkinsfile`. Run Build Now once to install
   the polling trigger. No public webhook is required for a local controller.

The build agent uses a separate TLS-protected Docker-in-Docker daemon. Its
privileged container is needed by Docker-in-Docker; no host Docker socket or
host port is exposed. Do not run untrusted pull-request code on this trusted
release agent. Its Maven cache, work directory and Docker images use named
volumes. `docker compose -f ci/jenkins/compose.yml stop` stops CI infrastructure
without deleting those volumes or touching application containers.

## Disposable test database

`ci/database/schema.sql` is a schema-only snapshot of the local PostgreSQL 18.6
database. It contains no application records, passwords or grants. It preserves
the existing check constraints and indexes that Hibernate `create` would miss.
Update this snapshot when the application schema changes; this is not a
production migration or a database backup. The first CI runs found the local
snapshot lacked `flight_clearance_state_check` from the existing API guide.
`ci/database/constraints.sql` applies that documented constraint only to
`ci_aviation`, with a database-name guard. The local application database and RDS
were not changed. Check this schema drift before enabling cloud deployment;
Hibernate schema validation does not verify every check constraint.

Each build creates `flight-ci-postgres` inside the dedicated CI engine, loads
the schema, and runs `cisetup.CiFixtureSetup` to generate synthetic fixtures using
the existing seeders. The initializer refuses any database URL other than
`jdbc:postgresql://docker:55432/ci_aviation`. Normal tests keep schema validation
and scheduling disabled. SMTP and payment settings are deliberately nonworking;
the existing in-process notification substitutes are used by service fixtures.
No local `.env`, ignored application settings or RDS credentials are copied.

The same disposable PostgreSQL container also creates `airline_admin_test` for
the five opt-in repository tests. Only that separate database uses Hibernate
`create-drop`, as required by the existing test class. The main test database
continues using the imported schema and validation.

The pipeline removes only the database container carrying its disposable-CI
label. After a hard interruption, inspect the agent's CI engine before running
`bash ci/backend.sh cleanup`. Never run this command against the application
Docker engine. The pipeline refuses to silently reuse an existing test database.

Database/fixture tests do not establish real Kafka or Feign transport coverage.
The separate existing `DockerTestRunner` is not part of this initial pipeline.

The integration suite runs sequentially with Spring's context cache limited to
one context. Its existing `IdentityFixtures` helper stores a static EntityManager;
reusing an older cached context after another context replaces that field mixes
transaction managers. This CI setting avoids that shared-state problem without
changing business code or excluding flight-operation tests. A future test cleanup
should make the helper an injected instance rather than global state.

## References and assistance

- Jenkins Docker setup: https://www.jenkins.io/doc/book/installing/docker/
- Jenkinsfile: https://www.jenkins.io/doc/book/pipeline/jenkinsfile/
- Inbound agent: https://github.com/jenkinsci/remoting/blob/master/docs/inbound-agent.md
- Codex assisted with the pipeline, disposable fixtures, setup and verification.
