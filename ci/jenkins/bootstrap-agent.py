"""Run after register-agent.groovy. Never prints or stores the agent secret on the host."""
import json
import pathlib
import subprocess

root = pathlib.Path(__file__).resolve().parents[2]
compose = ["docker", "compose", "-f", str(root / "ci/jenkins/compose.yml")]
subprocess.run(compose + ["up", "-d", "docker"], check=True)
subprocess.run(compose + ["create", "agent"], check=True)
networks = json.loads(subprocess.check_output([
    "docker", "inspect", "jenkins-local", "--format", "{{json .NetworkSettings.Networks}}"
]))
if "flight-jenkins" not in networks:
    subprocess.run(["docker", "network", "connect", "flight-jenkins", "jenkins-local"], check=True)
secret = subprocess.check_output([
    "docker", "exec", "jenkins-local", "cat", "/var/jenkins_home/flight-agent-connection-secret"
])
if len(secret.strip()) < 32:
    raise RuntimeError("Missing agent secret; run register-agent.groovy first")
subprocess.run([
    "docker", "run", "--rm", "-i", "--network", "none", "--user", "root",
    "-v", "flight-jenkins_agent-work:/data", "--entrypoint", "sh",
    "sauds-flight-jenkins-agent:local", "-c",
    "umask 077; cat > /data/connection-secret; chown 1000:1000 /data/connection-secret"
], input=secret, check=True)
del secret
subprocess.run(["docker", "start", "flight-jenkins-agent"], check=True)
subprocess.run([
    "docker", "exec", "jenkins-local", "rm", "/var/jenkins_home/flight-agent-connection-secret"
], check=True)
print("Agent started; verify flight-backend-agent is online in Jenkins.")
