package br.com.ctcore.identity;

/** RN-ACE-21: the six actions that ask for the password again (and the code, when two-step is on). */
public enum CriticalAction {
    TRANSFER_COORDINATION, CANCEL_SUBSCRIPTION, FULL_EXPORT, DISCONNECT_MERCADO_PAGO, ANONYMIZE_STUDENT, DEACTIVATE_UNIT
}
