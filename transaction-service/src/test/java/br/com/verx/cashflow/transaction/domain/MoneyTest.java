package br.com.verx.cashflow.transaction.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void deveCriarMoneyValida() {
        Money money = Money.of(new BigDecimal("1500.00"), "BRL");

        assertThat(money.amount()).isEqualByComparingTo("1500.0000");
        assertThat(money.currency()).isEqualTo("BRL");
        assertThat(money.toContractString()).isEqualTo("1500.0000");
    }

    @Test
    void deveNormalizarEscalaParaQuatroCasas() {
        Money money = Money.parse("10.5", "BRL");

        assertThat(money.toContractString()).isEqualTo("10.5000");
    }

    @Test
    void deveRejeitarValorZero() {
        assertThatThrownBy(() -> Money.of(BigDecimal.ZERO, "BRL"))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("maior que zero");
    }

    @Test
    void deveRejeitarValorNegativo() {
        assertThatThrownBy(() -> Money.of(new BigDecimal("-10.00"), "BRL"))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("maior que zero");
    }

    @Test
    void deveRejeitarMaisDeQuatroCasasDecimais() {
        assertThatThrownBy(() -> Money.of(new BigDecimal("10.12345"), "BRL"))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("4 casas decimais");
    }

    @ParameterizedTest
    @ValueSource(strings = {"brl", "BR", "BRLX", "123", ""})
    void deveRejeitarMoedaForaDoFormatoIso4217(String currency) {
        assertThatThrownBy(() -> Money.of(new BigDecimal("10.00"), currency))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("currency inválida");
    }

    @Test
    void deveRejeitarAmountNulo() {
        assertThatThrownBy(() -> Money.of(null, "BRL"))
                .isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void deveConsiderarIgualdadePorValorIgnorandoEscalaOriginal() {
        Money a = Money.parse("10.5", "BRL");
        Money b = Money.parse("10.5000", "BRL");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void deveRejeitarStringDeAmountInvalida() {
        assertThatThrownBy(() -> Money.parse("abc", "BRL"))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("formato decimal");
    }

    @Test
    void deveRejeitarMaisDeQuinzeDigitosInteiros() {
        assertThatThrownBy(() -> Money.parse("1234567890123456.0000", "BRL"))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("formato decimal");
    }
}
