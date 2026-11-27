ALTER TABLE gift_contacts
    ADD COLUMN gift_member BOOLEAN NOT NULL DEFAULT TRUE,
    ADD KEY ix_gift_contacts_member (ledger_id, gift_member, name, distinction);

ALTER TABLE rental_contracts
    MODIFY COLUMN landlord_name VARCHAR(60) NOT NULL,
    ADD COLUMN landlord_contact_id VARCHAR(48),
    ADD COLUMN landlord_distinction VARCHAR(60),
    ADD KEY ix_rental_contract_landlord (ledger_id, landlord_contact_id),
    ADD CONSTRAINT fk_rental_contract_landlord FOREIGN KEY (ledger_id, landlord_contact_id)
        REFERENCES gift_contacts (ledger_id, id);
