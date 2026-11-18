# Backend CI with Jenkins

`Jenkinsfile` belongs to the backend repository. Jenkins polls its configured Git
branch every five minutes, builds the shared library and five Java 17 services,
runs Aviation, Identity and integration tests (including concurrency), explicitly runs the
14-scenario Cucumber runner, and builds five Docker images from the tested JARs.
Every Maven and Docker command must succeed. Reports and application JARs are
archived even after failures. Image tags are `<12-character-commit>-<build-number>`.

The CI pipeline also publishes the five tested images to private ECR repositories
in account `425989919753`, region `eu-north-1`. A separate `flight-backend-release`
job uses `Jenkinsfile.release` to check or deploy a selected CI image tag. A green
CI build means images are available for release; it does not mean EC2 was updated.

## Verified on 18 November 2026

Local Jenkins build [#4](http://localhost:8082/job/flight-backend-ci/4/) passed for
code commit `2a883676d6582dd34c82bc7acd0128adcfdb83d7`: **291 tests, zero failures,
zero errors, zero skips**, including all 14 Cucumber scenarios. All five images
were built with tag `2a883676d658-4`. Independent SHA-256 comparisons confirmed
each image contains the exact corresponding tested JAR. Jenkins archived the
reports and JARs and removed the disposable PostgreSQL container.

That run used a Git-generated local source archive before the branch was
published. The live job now loads `Jenkinsfile` from GitHub branch `jenkins-ci`,
with polling configured every five minutes. The first GitHub checkout succeeded;
its run exposed a missing commit environment variable. The pipeline now captures
the revision returned by `checkout scm` explicitly for its image tags. GitHub-backed
build #6 passed all 291 tests and built five images; scheduled polling then checked
the GitHub branch against that successful checkout. Publishing/release support was
added afterward and must be verified by its own Jenkins run.

Earlier failed runs confirmed that test failures stop image building. The CI
fixes preserve application behavior: use its existing JPA settings, isolate the
legacy test context, install the documented constraint in the disposable
database, and use HTTPS package mirrors for image construction.

## What the files are for

| File | Purpose | When used |
| --- | --- | --- |
| Root `Jenkinsfile` | Defines pipeline stages, reports and Git polling | Every build |
| `ci/backend.sh` | Builds Java, prepares test data, runs tests and builds images | Every build |
| `ci/application.properties` | Synthetic test settings pointing only to disposable PostgreSQL | Tests |
| `ci/Dockerfile` | Packages each tested service JAR into a runtime image | Image builds |
| `ci/aws.sh`, `ci/publish.sh` | Verify the restricted AWS identity and publish tested images | CI publishing |
| Root `Jenkinsfile.release`, `ci/deploy.sh`, `ci/deploy/` | Check and deploy selected image digests; include isolated rollback tests | Manual release job |
| `ci/database/schema.sql` | Empty database structure, including indexes and constraints | Test database creation |
| `ci/database/constraints.sql` | Adds the documented clearance constraint missing from the snapshot | Test database creation |
| `ci/jenkins/Dockerfile` | Defines the Jenkins worker with Java 17 and Docker tools | Worker setup/rebuild |
| `ci/jenkins/compose.yml` | Runs the worker and its separate Docker engine | CI infrastructure startup |
| `ci/jenkins/register-agent.groovy` | Registers the dedicated worker in Jenkins | Initial setup/recovery |
| `ci/jenkins/bootstrap-agent.py` | Connects that worker without displaying its connection secret | Initial setup/recovery |
| `ci/jenkins/register-deploy-key.groovy` | Stores the approved EC2 key in Jenkins without printing it | Initial credential setup |
| `ci/.gitattributes`, `ci/.gitignore` | Keep Linux line endings and exclude Python cache files | Repository maintenance |
| `ci/README.md` | Instructions and verification notes only | Human reference |

These files support the current setup; they are not all documentation. The setup
helpers are retained so the worker can be recreated. The SQL files are needed by
this pipeline's database tests. They are **not applied to AWS RDS**. The deployed
application keeps its existing RDS configuration; CI creates and deletes its own
PostgreSQL instance with synthetic records.

## Starting Jenkins and its agent

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

The read-only RDS inspection on 18 November also found that constraint missing.
The release preflight deliberately blocks deployments until a separately reviewed
migration installs and validates it. Neither pipeline applies SQL to RDS.

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

## ECR credentials and release workflow

The verified AWS account is `425989919753`; use the local CLI profile
`flight-project` with explicit `--region eu-north-1` for administration. The old
`uob-student` profile belongs to an unrelated training account and must not be used
for this project. The browser-based administration session must not be installed
in Jenkins. Jenkins authenticates as IAM user `flight-jenkins-publisher`, with the
`FlightJenkinsEcrPublish` policy limited to the five project image repositories.

Jenkins credentials:

- `flight-ecr-publisher`: **Username with password**, username = access key ID,
  password = secret access key. These are bound only during AWS operations.
- `flight-ec2-deploy`: **SSH Username with private key**, username `ec2-user`,
  existing authorized EC2 key. SSH verifies the already-trusted host key in
  `ci/deploy/known_hosts`; it never disables host verification.

The publisher uses the official AWS CLI 2.32.5 container pinned by digest. It
requires the exact IAM user ARN and verifies each local image's commit label.
ECR repositories use immutable tags and scan-on-push. Scanning is enabled but
scan findings are not yet an enforced release gate. `release-tag.txt` and
`release-images.tsv` are archived with a successful CI build. A partial publish
is a failed build; use only tags from successful complete builds.

Create `flight-backend-release` as Pipeline script from SCM using this repository,
branch `*/jenkins-ci`, script path `Jenkinsfile.release`. It does not poll GitHub.
Choose **Build with Parameters**, paste a successful CI `IMAGE_TAG`, and keep
`CHECK_ONLY=true` first. This verifies all five published digests, SSH, existing
service health, the expected private RDS connection with verified TLS, and the
required clearance constraint. It performs no deployment or database writes.

After a successful check, run with the same tag and `CHECK_ONLY=false` to release.
The release locks the host deployment directory, repeats the read-only checks,
pulls all five digests, backs up Compose, replaces only application image
references, and restarts the services sequentially. Runtime secrets, RDS schema,
uploaded files, Kafka data, Redis/Kafka image versions and nginx configuration
remain in place. Docker registry authentication is temporary and removed on exit.
The deployed Compose file pins image digests, so the existing startup script also
uses those images. `current-release.txt` records the selected tag.

Startup or HTTP verification failure restores the previous Compose file and
attempts to restart the previous images. Rollback failure remains a failed job
requiring inspection. A process kill or host failure may prevent the shell error
handler from running; inspect the private `releases/<tag>/compose.before.yml`
backup before recovery. There are no automatic database migrations or database
rollbacks. Releases can briefly interrupt requests on this single small EC2 host.

Run the isolated release checks (no network inside the test container):

```powershell
docker run --rm --network none --mount type=bind,source=${PWD}/ci/deploy,target=/work,readonly python:3.13-slim bash /work/selftest.sh
```

The tests cover foreign/missing/duplicate image references, preservation of runtime
configuration, a simulated mid-release failure with rollback, successful rollout,
and temporary registry credential cleanup. Real cloud deployment still requires
its own smoke checks.

## References and assistance

- Jenkins Docker setup: https://www.jenkins.io/doc/book/installing/docker/
- Jenkinsfile: https://www.jenkins.io/doc/book/pipeline/jenkinsfile/
- Inbound agent: https://github.com/jenkinsci/remoting/blob/master/docs/inbound-agent.md
- Codex assisted with the pipeline, disposable fixtures, setup and verification.
