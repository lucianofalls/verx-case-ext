package br.com.verx.cashflow.transaction.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate Root do Bounded Context Cash Flow / Position Keeping, responsável pela capability Financial Entry Management.
 *
 * <p>Invariantes:
 * <ul>
 *   <li>{@code transactionId} é sempre gerado pelo servidor;</li>
 *   <li>{@code businessDate} é derivado de {@code occurredAt} em {@link #BUSINESS_TIMEZONE};</li>
 *   <li>lançamentos são imutáveis — correções usam um novo lançamento compensatório;</li>
 *   <li>{@code amount} é sempre positivo e o sinal vem de {@link TransactionType};</li>
 *   <li>uma reversão referencia o lançamento original, sem alterá-lo.</li>
 * </ul>
 */
public final class FinancialTransaction {

    public static final ZoneId BUSINESS_TIMEZONE = ZoneId.of("America/Sao_Paulo");
    private static final int MAX_DESCRIPTION_LENGTH = 500;

    private final UUID transactionId;
    private final String merchantId;
    private final TransactionType type;
    private final Money amount;
    private final String description;
    private final Instant occurredAt;
    private final LocalDate businessDate;
    private final String idempotencyKey;
    private final Instant createdAt;
    private final UUID reversalOfTransactionId;

    private FinancialTransaction(
            UUID transactionId,
            String merchantId,
            TransactionType type,
            Money amount,
            String description,
            Instant occurredAt,
            LocalDate businessDate,
            String idempotencyKey,
            Instant createdAt,
            UUID reversalOfTransactionId
    ) {
        this.transactionId = transactionId;
        this.merchantId = merchantId;
        this.type = type;
        this.amount = amount;
        this.description = description;
        this.occurredAt = occurredAt;
        this.businessDate = businessDate;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
        this.reversalOfTransactionId = reversalOfTransactionId;
    }

    public static FinancialTransaction record(
            String merchantId,
            TransactionType type,
            Money amount,
            String description,
            Instant occurredAt,
            String idempotencyKey,
            Clock clock
    ) {
        validateCommon(merchantId, type, amount, description, occurredAt, idempotencyKey, clock);

        return new FinancialTransaction(
                UUID.randomUUID(),
                merchantId,
                type,
                amount,
                description,
                occurredAt,
                occurredAt.atZone(BUSINESS_TIMEZONE).toLocalDate(),
                idempotencyKey,
                Instant.now(clock),
                null
        );
    }

    /**
     * Cria um lançamento compensatório imutável. O original permanece intacto.
     */
    public static FinancialTransaction reverse(
            FinancialTransaction original,
            String description,
            Instant occurredAt,
            String idempotencyKey,
            Clock clock
    ) {
        if (original == null) {
            throw new InvalidTransactionException("lançamento original não pode ser nulo");
        }
        if (original.reversalOfTransactionId() != null) {
            throw new InvalidTransactionException("não é permitido reverter um lançamento que já é reversão");
        }

        validateCommon(
                original.merchantId(),
                original.type().opposite(),
                original.amount(),
                description,
                occurredAt,
                idempotencyKey,
                clock);

        return new FinancialTransaction(
                UUID.randomUUID(),
                original.merchantId(),
                original.type().opposite(),
                original.amount(),
                description,
                occurredAt,
                occurredAt.atZone(BUSINESS_TIMEZONE).toLocalDate(),
                idempotencyKey,
                Instant.now(clock),
                original.transactionId()
        );
    }

    public static FinancialTransaction reconstitute(
            UUID transactionId,
            String merchantId,
            TransactionType type,
            Money amount,
            String description,
            Instant occurredAt,
            LocalDate businessDate,
            String idempotencyKey,
            Instant createdAt,
            UUID reversalOfTransactionId
    ) {
        Objects.requireNonNull(transactionId, "transactionId não pode ser nulo");
        Objects.requireNonNull(businessDate, "businessDate não pode ser nulo");
        Objects.requireNonNull(createdAt, "createdAt não pode ser nulo");
        return new FinancialTransaction(
                transactionId, merchantId, type, amount, description,
                occurredAt, businessDate, idempotencyKey, createdAt, reversalOfTransactionId);
    }

    public FinancialTransactionRecorded toRecordedEvent() {
        return FinancialTransactionRecorded.from(this);
    }

    private static void validateCommon(
            String merchantId,
            TransactionType type,
            Money amount,
            String description,
            Instant occurredAt,
            String idempotencyKey,
            Clock clock
    ) {
        requireNonBlank(merchantId, "merchantId");
        requireNonBlank(idempotencyKey, "idempotencyKey");
        if (type == null) {
            throw new InvalidTransactionException("type não pode ser nulo");
        }
        if (amount == null) {
            throw new InvalidTransactionException("amount não pode ser nulo");
        }
        if (occurredAt == null) {
            throw new InvalidTransactionException("occurredAt não pode ser nulo");
        }
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new InvalidTransactionException(
                    "description não pode exceder " + MAX_DESCRIPTION_LENGTH + " caracteres");
        }
        if (clock == null) {
            throw new InvalidTransactionException("clock não pode ser nulo");
        }
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidTransactionException(field + " não pode ser vazio");
        }
    }

    public UUID transactionId() {
        return transactionId;
    }

    public String merchantId() {
        return merchantId;
    }

    public TransactionType type() {
        return type;
    }

    public Money amount() {
        return amount;
    }

    public String description() {
        return description;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    public LocalDate businessDate() {
        return businessDate;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public UUID reversalOfTransactionId() {
        return reversalOfTransactionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FinancialTransaction that)) return false;
        return transactionId.equals(that.transactionId);
    }

    @Override
    public int hashCode() {
        return transactionId.hashCode();
    }

    @Override
    public String toString() {
        return "FinancialTransaction{" +
                "transactionId=" + transactionId +
                ", merchantId='" + merchantId + '\'' +
                ", type=" + type +
                ", amount=" + amount +
                ", businessDate=" + businessDate +
                ", reversalOfTransactionId=" + reversalOfTransactionId +
                '}';
    }
}
