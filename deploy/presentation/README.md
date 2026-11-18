# Presentation dataset tools

These scripts describe a fictional November 2026 demo dataset. They depend on the
application schema and specific existing seed IDs; they are not a general-purpose
production migration or an automatically applied seed.

`build_data.py` generates `generated.sql` and `accounts.private.json` locally.
By default the SQL ends with `ROLLBACK`; `--commit` generates a committing script.
Generation itself does not connect to a database. It requires Java and the Maven
dependencies referenced in the script. Review the generated SQL and take a verified
backup before applying it. The SQL refuses a second import.

The SSH tools require explicit host and key arguments. Database connection details
and credentials are read from the remote Identity container, with verified TLS:

```powershell
python deploy/presentation/rds_tool.py deploy/presentation/sample-scope.sql --host EC2_HOST --key /path/to/ssh-key.pem --output deploy/presentation/scope.private.txt
python deploy/presentation/verify_accounts.py --host EC2_HOST --key /path/to/ssh-key.pem
```

Replace the placeholders locally. `rds_tool.py` wraps SQL in a read-only transaction
unless `--write` is explicitly supplied. Its query output can include database data;
save output only to ignored private files. `verify_accounts.py` makes actual login
attempts and GET requests, then writes ignored `verification.jsonl` output.

`write_guide.py` reads the private manifest and writes `PRESENTATION.private.txt`,
including its shared demo password. The guide describes the intended walkthrough;
its presence does not prove that data is imported or verification has passed.
Replace connection placeholders only in your private copy.

Keep `accounts.private.json`, `PRESENTATION.private.txt`, `generated.sql`, and all
verification output out of Git. The existing local verification file is intentionally
preserved and ignored.
