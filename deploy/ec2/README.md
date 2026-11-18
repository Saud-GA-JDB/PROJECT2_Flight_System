# Existing EC2 + private RDS deployment

This standalone configuration runs the five Java services, Redis, and Kafka on
the existing Amazon Linux host. It does not create AWS resources or restore a
database. The local `docker-compose.yml` remains independent.

The initial deployment is private verification: Gateway listens only on host
loopback, all other container ports are private, seeders are disabled, and
`APP_SCHEDULING_ENABLED=false` prevents scheduled changes/email retries against
the migrated data. Enable scheduled work only at a deliberate cutover, after
checking whether the local source received writes since its migration.

## Files and secrets

Copy the Compose file and shell scripts to `/opt/sauds-flight-system`. The directory
is mode 700. `runtime.env` is mode 600 and must never be committed or printed.
`configure-private-env.py` opens a masked local dialog and sends the entered RDS
password and existing ignored root `.env` application settings through SSH.
It does not extract pgAdmin credentials or save a new local plaintext secret file.
It refuses to overwrite an existing remote `runtime.env`.

```powershell
python deploy/ec2/configure-private-env.py --host EC2_HOST --key /path/to/ssh-key.pem --db-host RDS_HOST --image-tag RELEASE_TAG
```

Replace the uppercase placeholders with your deployment values. The EC2 address can change after stop/start.
`runtime.env.example` lists the expected settings without credentials.
The generated private `runtime.env` includes `RDS_HOST`; when updating an older
installation, add that setting to its existing private file before using this Compose file.
The configuration helper refuses to overwrite an existing file.
Required application settings are the same service keys, JWT, Stripe sandbox,
SMTP credentials, and return URLs used in the local `.env`. RDS has a separate
password. Review frontend origins and payment return URLs before public use.

Identity, Aviation, and Maintenance connect directly to the private RDS DNS
endpoint with `sslmode=verify-full` and a read-only mounted AWS CA bundle.
Hibernate remains on `validate`; no PostgreSQL container is launched.
Connection pools are limited to three per service.

## Existing-schema prerequisite

If the database lacks the recovery-question columns required by Identity,
create and verify a backup before reviewing `upgrade-recovery-question.sql`.
It adds nullable code hash/expiry columns and a non-null failed-attempt counter
(default zero), checking that existing user fields remain unchanged.

This script deliberately refuses replay if any recovery column exists. Do not run
it blindly. `database-admin.py inspect` reads schema/counts through verified TLS;
`database-admin.py backup` creates a private backup before a reviewed upgrade.
Credentials are read in memory from the existing Identity container configuration.

## Build and deploy

Build the application images on the workstation with `docker compose build`,
tag each `sauds-flight-system-SERVICE` image with an immutable release tag, export
with `docker save`, and transfer through SSH. Do not upload `.env` in the build
context. Load the archive with `sudo docker load`.
The Compose file intentionally refuses registry pulls for application images.

For a slow workstation upload, `bash build-on-host.sh RELEASE_TAG` is a fallback. Upload a `git
archive` of the tracked build sources as `source.tar.gz`; it builds one service
at a time with a 512 MiB Maven heap and a 1.4 GiB build-container limit. Run it
before application startup. It uses Docker's legacy builder to apply those
container limits and needs no buildx plugin. This consumes burst CPU credits;
the installed image can be reused afterward. The script's release tag must match
`IMAGE_TAG` in the private runtime configuration.

`prepare-host.sh` installs the official AWS RDS CA bundle, a checksum-verified
Compose plugin, and a 2 GiB swap file on the existing root disk. Docker itself is
installed from the Amazon Linux repository with `sudo dnf install -y docker`
and enabled with `sudo systemctl enable --now docker`.

```bash
cd /opt/sauds-flight-system
bash start-backend.sh
bash verify-backend.sh
```

Startup is sequential to reduce memory pressure. Each container has a memory
limit, the JVMs have explicit heap limits, and logs rotate. These are trial
settings for a small educational deployment, not proof of adequate load capacity.
Swap uses the existing encrypted disk and does not resize it. Watch swap activity,
OOM exits, CPU credits, latency, and disk usage before accepting public traffic.
The total container limits exceed physical RAM, so workload verification matters.

Kafka persists in a named volume; Redis is disposable. Identity uses host
`uploads/`. Database backups do not include uploaded files. Verify database image references and any prior upload directories before declaring
file migration complete.

## Private access and public cutover

```powershell
ssh -N -L 18085:127.0.0.1:8085 -i /path/to/ssh-key.pem ec2-user@EC2_HOST
```

Then access `http://localhost:18085` through the encrypted tunnel. Do not submit
passwords or bearer tokens to a public plain-HTTP endpoint.

Before public release, provide a domain/frontend origin, configure HTTPS on the
existing host, set exact `FRONTEND_ORIGINS` and browser payment return URLs,
verify a real password login and authenticated read, and choose the cutover
point for scheduled jobs. SMTP and payment calls require separate deliberate
tests; health checks do not establish that they work.

For private login verification, upload `verify-login.py` to the deployment directory
and run `check-private-login.py --host HOST --key KEY_PATH` on Windows. The masked
dialog makes one actual login attempt and reads `/auth/users/me` and `/flights/search`
over the SSH connection. It reports only HTTP statuses and the flight-search count;
credentials, tokens, and profile data are not logged. Login can update the normal
login-attempt counters. The safe result is saved remotely as `login-verification.json`.

`unless-stopped` plus the enabled Docker service restarts existing containers
after reboot. It does not guarantee dependency readiness during a reboot;
verify recovery explicitly. Stop with `docker compose --env-file runtime.env
-f compose.yml stop`. Preserve the Kafka volume, uploads, RDS, and migration backup.

References: [Amazon Linux Docker installation](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/install-docker.html),
[Compose installation](https://docs.docker.com/compose/install/linux/),
[PostgreSQL JDBC certificate verification](https://jdbc.postgresql.org/documentation/ssl/).
