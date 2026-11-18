# Deployment tooling

- [ec2/](ec2/README.md): private EC2 deployment with an existing RDS database.
- [presentation/](presentation/README.md): fictional demo-data preparation and verification.

Commit scripts, Compose configuration, SQL source, documentation, and blank example
configuration. Keep actual configuration and generated output local. The ignore
rules cover runtime environment files, private account manifests and login sheets,
SSH keys, backups, generated SQL, archives, uploads, and verification logs.

For ad hoc database exports or diagnostic output, use an ignored `backups/`
directory or a `*.private.*` filename. Arbitrarily named output files are not
automatically safe to commit. Before committing, inspect
`git status --short --untracked-files=all -- deploy` and the staged diff;
do not force-add ignored files.

No script runs automatically just because this directory is in Git. Database
imports, schema changes, host preparation, and login checks are explicit operations.
