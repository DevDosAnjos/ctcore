-- RF-COB-04, RN-COB-16: the "Pagamento confirmado" e-mail joins the allowed templates and the receipt PDF (no session, by code) reads its data from one function.
-- Revert (manual, no data to lose outside these objects): drop function receipt_pdf_data(text); restore email_message_template_check without PAYMENT_CONFIRMED (V29 text).
alter table email_message drop constraint email_message_template_check;
alter table email_message add constraint email_message_template_check
    check (template in ('INVITE', 'PASSWORD_RESET', 'PASSWORD_CHANGED', 'NEW_ACCESS', 'COORDINATION_TRANSFERRED',
                        'EMAIL_CHANGE', 'EMAIL_CHANGED', 'GUARDIAN_UNLINKED', 'STUDENT_MAJORITY', 'PAYMENT_CONFIRMED'));

-- Same technique as public_receipt (V39): owner rights, one receipt by exact code, only what the PDF prints. The payer is the guardian when the charge has one.
create function receipt_pdf_data(p_code text)
returns table (organization_id uuid, logo text, unit_name text, cnpj text, address text, timezone text, number integer, code text, amount_cents bigint,
               fee_passed_cents bigint, method text, paid_at timestamptz, student_name text, payer_name text, payer_cpf text, reference text)
language sql stable security definer set search_path = public, pg_temp as $$
    select r.organization_id, o.logo_url, u.name, u.cnpj, u.address, u.timezone, r.number, r.code, p.amount_cents, p.fee_passed_cents, p.method, p.paid_at,
           s.name, coalesce(g.name, s.name), coalesce(g.cpf, s.cpf),
           case when c.type = 'MONTHLY' and c.reference_month is not null
                then m.name || ' · ' || (array['jan','fev','mar','abr','mai','jun','jul','ago','set','out','nov','dez'])[extract(month from c.reference_month)::int]
                     || '/' || extract(year from c.reference_month)::int
                else coalesce(c.description, '') end
    from receipt r
    join organization o on o.id = r.organization_id
    join payment p on p.id = r.payment_id
    join charge c on c.id = p.charge_id
    join unit u on u.id = r.unit_id
    join student s on s.id = c.student_id
    left join enrollment_modality em on em.id = c.enrollment_modality_id
    left join modality m on m.id = em.modality_id
    left join guardian g on g.id = c.payer_guardian_id
    where r.code = p_code
$$;
