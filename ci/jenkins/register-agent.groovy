// Run in Manage Jenkins > Script Console, while signed in as an administrator.
import jenkins.model.Jenkins
import hudson.model.Node
import hudson.slaves.DumbSlave
import hudson.slaves.JNLPLauncher
import hudson.slaves.RetentionStrategy
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

def j = Jenkins.get()
def node = j.getNode('flight-backend-agent')
if (node == null) {
    node = new DumbSlave('flight-backend-agent', '/home/jenkins/agent', new JNLPLauncher())
    node.setNumExecutors(1)
    node.setLabelString('flight-backend java17 docker')
    node.setMode(Node.Mode.EXCLUSIVE)
    node.setRetentionStrategy(new RetentionStrategy.Always())
    node.setNodeDescription('Backend CI: Java 17, separate Docker engine, disposable database')
    j.addNode(node)
}
def secretFile = new File(j.rootDir, 'flight-agent-connection-secret')
Files.writeString(secretFile.toPath(), node.toComputer().getJnlpMac())
Files.setPosixFilePermissions(secretFile.toPath(), PosixFilePermissions.fromString('rw-------'))
println('Agent registered; connection secret staged privately for bootstrap-agent.py')
