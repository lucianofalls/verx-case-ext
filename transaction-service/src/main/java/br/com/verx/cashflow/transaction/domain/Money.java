package br.com.verx.cashflow.transaction.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value Object imutável para valores monetários.
 *
 * <p>Regras (fechadas na documentação do case):
 * <ul>
 *   <li>nunca usar {@code float}/{@code double} — sempre {@link BigDecimal};</li>
 *   <li>precisão de armazenamento é {@code NUMERIC(19,4)} — no máximo 4 casas
 *       decimais, sem arredondamento silencioso: mais de 4 casas é erro, não
 *       aproximação;</li>
 *   <li>valor sempre positivo — o sinal (crédito/débito) é responsabilidade
 *       de {@link TransactionType}, não de {@code Money};</li>
 *   <li>moeda no formato ISO 4217 (3 letras maiúsculas).</li>
 * </ul>
 */
public final class Money {

    private static final int SCALE = 4;
    private static final int MAX_INTEGER_DIGITS = 15;
    private static final Pattern CONTRACT_AMOUNT_PATTERN = Pattern.compile("^\\d{1,15}(?:\\.\\d{1,4})?$");
    private static final Pattern CURRENCY_PATTERN = Pattern.compile("^[A-Z]{3}$");

    private final BigDecimal amount;
    private final String currency;

    private Money(BigDecimal amount, String currency) {
        this.amount = amount;
        this.currency = currency;
    }

    public static Money of(BigDecimal amount, String currency) {
        if (amount == null) {
            throw new InvalidTransactionException("amount não pode ser nulo");
        }
        if (amount.scale() > SCALE) {
            throw new InvalidTransactionException(
                    "amount não pode ter mais de " + SCALE + " casas decimais: " + amount);
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException("amount deve ser maior que zero: " + amount);
        }
        if (amount.precision() - amount.scale() > MAX_INTEGER_DIGITS) {
            throw new InvalidTransactionException("amount excede NUMERIC(19,4): " + amount);
        }
        if (currency == null || !CURRENCY_PATTERN.matcher(currency).matches()) {
            throw new InvalidTransactionException("currency inválida (esperado ISO 4217, ex. BRL): " + currency);
        }
        BigDecimal normalized = amount.setScale(SCALE, RoundingMode.UNNECESSARY);
        return new Money(normalized, currency);
    }

    /**
     * Parse a partir do formato decimal do contrato REST. O endpoint aceita
     * até 15 dígitos inteiros e até 4 casas decimais; o evento publicado é
     * normalizado para exatamente 4 casas por {@link #toContractString()}.
     */
    public static Money parse(String amount, String currency) {
        if (amount == null || amount.isBlank()) {
            throw new InvalidTransactionException("amount não pode ser vazio");
        }
        if (!CONTRACT_AMOUNT_PATTERN.matcher(amount).matches()) {
            throw new InvalidTransactionException("amount não está no formato decimal do contrato: " + amount);
        }
        try {
            return of(new BigDecimal(amount), currency);
        } catch (NumberFormatException e) {
            throw new InvalidTransactionException("amount não é um decimal válido: " + amount);
        }
    }

    public BigDecimal amount() {
        return amount;
    }

    public String currency() {
        return currency;
    }

    /** Representação no formato do contrato (ex. "1500.0000"). */
    public String toContractString() {
        return amount.toPlainString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money money)) return false;
        return amount.compareTo(money.amount) == 0 && currency.equals(money.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.stripTrailingZeros(), currency);
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency;
    }
}
