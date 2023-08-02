package app.k9mail.feature.account.setup.ui

import androidx.lifecycle.viewModelScope
import app.k9mail.core.ui.compose.common.mvi.BaseViewModel
import app.k9mail.feature.account.setup.domain.DomainContract
import app.k9mail.feature.account.setup.domain.DomainContract.UseCase
import app.k9mail.feature.account.setup.ui.AccountSetupContract.Effect
import app.k9mail.feature.account.setup.ui.AccountSetupContract.Event
import app.k9mail.feature.account.setup.ui.AccountSetupContract.SetupStep
import app.k9mail.feature.account.setup.ui.AccountSetupContract.State
import app.k9mail.feature.account.setup.ui.autodiscovery.AccountAutoDiscoveryContract
import app.k9mail.feature.account.setup.ui.autodiscovery.toAccountSetupState
import app.k9mail.feature.account.setup.ui.incoming.AccountIncomingConfigContract
import app.k9mail.feature.account.setup.ui.incoming.toServerSettings
import app.k9mail.feature.account.setup.ui.incoming.toValidationState
import app.k9mail.feature.account.setup.ui.options.AccountOptionsContract
import app.k9mail.feature.account.setup.ui.options.toAccountOptions
import app.k9mail.feature.account.setup.ui.outgoing.AccountOutgoingConfigContract
import app.k9mail.feature.account.setup.ui.outgoing.toServerSettings
import app.k9mail.feature.account.setup.ui.outgoing.toValidationState
import app.k9mail.feature.account.setup.ui.validation.AccountValidationContract
import com.fsck.k9.mail.oauth.AuthStateStorage
import kotlinx.coroutines.launch

@Suppress("LongParameterList")
class AccountSetupViewModel(
    private val createAccount: UseCase.CreateAccount,
    override val incomingValidationViewModel: AccountValidationContract.ViewModel,
    override val outgoingViewModel: AccountOutgoingConfigContract.ViewModel,
    override val outgoingValidationViewModel: AccountValidationContract.ViewModel,
    override val optionsViewModel: AccountOptionsContract.ViewModel,
    private val authStateStorage: AuthStateStorage,
    private val accountSetupStateRepository: DomainContract.AccountSetupStateRepository,
    initialState: State = State(),
) : BaseViewModel<State, Event, Effect>(initialState), AccountSetupContract.ViewModel {

    override fun event(event: Event) {
        when (event) {
            is Event.OnAutoDiscoveryFinished -> onAutoDiscoveryFinished(event.state, event.isAutomaticConfig)

            Event.OnBack -> onBack()
            Event.OnNext -> onNext()
        }
    }

    private fun onAutoDiscoveryFinished(
        autoDiscoveryState: AccountAutoDiscoveryContract.State,
        isAutomaticConfig: Boolean,
    ) {
        updateState {
            it.copy(
                isAutomaticConfig = isAutomaticConfig,
            )
        }

        accountSetupStateRepository.save(autoDiscoveryState.toAccountSetupState())
        //TODO use account setup state?
        authStateStorage.updateAuthorizationState(autoDiscoveryState.authorizationState?.state)

        onNext()
    }

    private fun onNext() {
        when (state.value.setupStep) {
            SetupStep.AUTO_CONFIG -> {
                if (state.value.isAutomaticConfig) {
                    // TODO add state for incoming server settings
//                    incomingValidationViewModel.initState(incomingViewModel.state.value.toValidationState())
                    outgoingValidationViewModel.initState(outgoingViewModel.state.value.toValidationState())
                    changeToSetupStep(SetupStep.INCOMING_VALIDATION)
                } else {
                    changeToSetupStep(SetupStep.INCOMING_CONFIG)
                }
            }

            SetupStep.INCOMING_CONFIG -> {
                // TODO add state for incoming server settings
//                incomingValidationViewModel.initState(incomingViewModel.state.value.toValidationState())
                changeToSetupStep(SetupStep.INCOMING_VALIDATION)
            }

            SetupStep.INCOMING_VALIDATION -> {
                if (state.value.isAutomaticConfig) {
                    changeToSetupStep(SetupStep.OUTGOING_VALIDATION)
                } else {
                    changeToSetupStep(SetupStep.OUTGOING_CONFIG)
                }
            }

            SetupStep.OUTGOING_CONFIG -> {
                outgoingValidationViewModel.initState(outgoingViewModel.state.value.toValidationState())
                changeToSetupStep(SetupStep.OUTGOING_VALIDATION)
            }

            SetupStep.OUTGOING_VALIDATION -> {
                changeToSetupStep(SetupStep.OPTIONS)
            }

            SetupStep.OPTIONS -> onFinish()
        }
    }

    private fun onBack() {
        when (state.value.setupStep) {
            SetupStep.AUTO_CONFIG -> navigateBack()
            SetupStep.INCOMING_CONFIG -> changeToSetupStep(SetupStep.AUTO_CONFIG)
            SetupStep.INCOMING_VALIDATION -> {
                if (state.value.isAutomaticConfig) {
                    changeToSetupStep(SetupStep.AUTO_CONFIG)
                } else {
                    changeToSetupStep(SetupStep.INCOMING_CONFIG)
                }
            }

            SetupStep.OUTGOING_CONFIG -> changeToSetupStep(SetupStep.INCOMING_CONFIG)
            SetupStep.OUTGOING_VALIDATION -> {
                if (state.value.isAutomaticConfig) {
                    changeToSetupStep(SetupStep.AUTO_CONFIG)
                } else {
                    changeToSetupStep(SetupStep.OUTGOING_CONFIG)
                }
            }

            SetupStep.OPTIONS -> if (state.value.isAutomaticConfig) {
                changeToSetupStep(SetupStep.AUTO_CONFIG)
            } else {
                changeToSetupStep(SetupStep.OUTGOING_CONFIG)
            }
        }
    }

    private fun changeToSetupStep(setupStep: SetupStep) {
        if (setupStep == SetupStep.AUTO_CONFIG) {
            authStateStorage.updateAuthorizationState(authorizationState = null)
        }

        updateState {
            it.copy(
                setupStep = setupStep,
            )
        }
    }

    private fun onFinish() {
        val outgoingState = outgoingViewModel.state.value
        val optionsState = optionsViewModel.state.value

        val accountSetupState = accountSetupStateRepository.getState()

        viewModelScope.launch {
            val result = createAccount.execute(
                emailAddress = accountSetupState.emailAddress ?: "",
                incomingServerSettings = accountSetupState.incomingServerSettings!!,
                outgoingServerSettings = outgoingState.toServerSettings(),
                authorizationState = authStateStorage.getAuthorizationState(),
                options = optionsState.toAccountOptions(),
            )

            navigateNext(result)
        }
    }

    private fun navigateNext(accountUuid: String) = emitEffect(Effect.NavigateNext(accountUuid))

    private fun navigateBack() = emitEffect(Effect.NavigateBack)
}
