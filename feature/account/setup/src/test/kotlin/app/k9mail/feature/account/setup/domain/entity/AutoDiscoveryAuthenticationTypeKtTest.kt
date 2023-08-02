package app.k9mail.feature.account.setup.domain.entity

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.fsck.k9.mail.AuthType
import org.junit.Test

class AutoDiscoveryAuthenticationTypeKtTest {

    @Test
    fun `should map all AutoDiscoveryAuthenticationTypes`() {
        val types = AutoDiscoveryAuthenticationType.values()

        for (type in types) {
            val authenticationType = type.toAuthenticationType()

            assertThat(authenticationType).isEqualTo(
                when (type) {
                    AutoDiscoveryAuthenticationType.PasswordCleartext -> AuthenticationType.PasswordCleartext
                    AutoDiscoveryAuthenticationType.PasswordEncrypted -> AuthenticationType.PasswordEncrypted
                    AutoDiscoveryAuthenticationType.OAuth2 -> AuthenticationType.OAuth2
                },
            )
        }
    }
}
