// Run in the authenticated Jenkins Script Console after privately staging the
// already-authorized EC2 key at JENKINS_HOME/.flight-deploy-key (mode 600).
import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.SystemCredentialsProvider
import com.cloudbees.plugins.credentials.domains.Domain
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey

def provider = SystemCredentialsProvider.getInstance()
def staged = new File(jenkins.model.Jenkins.get().rootDir, '.flight-deploy-key')
try {
    assert staged.isFile(): 'Stage the authorized EC2 key privately first.'
    assert !provider.credentials.any { it.id == 'flight-ec2-deploy' }: 'Credential already exists; refusing replacement.'
    def credential = new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, 'flight-ec2-deploy', 'ec2-user',
        new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(staged.getText('UTF-8').trim() + '\n'),
        '', 'Existing flight EC2 deployment key; host pinned in ci/deploy/known_hosts')
    assert provider.store.addCredentials(Domain.global(), credential): 'Credential was not saved.'
    println('Stored flight-ec2-deploy in Jenkins. Private key contents were not displayed.')
} finally {
    java.nio.file.Files.deleteIfExists(staged.toPath())
}
