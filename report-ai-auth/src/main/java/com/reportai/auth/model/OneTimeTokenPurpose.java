package com.reportai.auth.model;

/**
 * Security-sensitive one-time credentials issued by the account lifecycle flows.
 *
 * <p>A token is always consumed for the purpose for which it was issued.  Keeping the purpose in
 * the persisted record prevents a password-reset credential from being replayed as an email
 * verification credential (or the reverse).</p>
 */
public enum OneTimeTokenPurpose {
    EMAIL_VERIFICATION,
    PASSWORD_RESET
}
