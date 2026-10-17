-- RN-ACE-10: o pedido de link de nova senha também conta para o limite por e-mail e por IP.
alter table login_attempt drop constraint login_attempt_result_check;
alter table login_attempt add constraint login_attempt_result_check
    check (result in ('SUCCESS', 'WRONG_PASSWORD', 'LOCKED', 'IP_LIMITED', 'CHALLENGE_FAILED', 'RESET_REQUEST'));
