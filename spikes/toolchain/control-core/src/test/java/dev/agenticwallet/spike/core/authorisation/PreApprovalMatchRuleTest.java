package dev.agenticwallet.spike.core.authorisation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.agenticwallet.spike.kernel.Money;
import dev.agenticwallet.spike.kernel.ids.DecisionId;
import dev.agenticwallet.spike.kernel.ids.MerchantReference;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The match rule at every edge; consumption is tested in the hook responder. */
class PreApprovalMatchRuleTest {

    private static final Instant FROM = Instant.parse("2026-11-01T10:00:00Z");
    private static final Instant UNTIL = Instant.parse("2026-11-08T10:00:00Z");
    private static final MerchantReference DEMO_SHOP = MerchantReference.parse("demo-shop");

    private final PreApproval preApproval =
            new PreApproval(
                    new DecisionId(UUID.fromString("00000000-0000-0000-0000-00000000d001")),
                    Money.of("80.00", "EUR"),
                    DEMO_SHOP,
                    FROM,
                    UNTIL);

    @Test
    void matchesAtTheAmountAndBelowInsideTheWindow() {
        assertThat(
                        PreApprovalMatchRule.matches(
                                preApproval, authorisation("80.00", "EUR", DEMO_SHOP, FROM)))
                .isTrue();
        assertThat(
                        PreApprovalMatchRule.matches(
                                preApproval, authorisation("79.99", "EUR", DEMO_SHOP, UNTIL)))
                .isTrue();
    }

    @Test
    void doesNotMatchOneCentOverAnotherCurrencyAnotherMerchantOrOutsideTheWindow() {
        assertThat(
                        PreApprovalMatchRule.matches(
                                preApproval, authorisation("80.01", "EUR", DEMO_SHOP, FROM)))
                .isFalse();
        assertThat(
                        PreApprovalMatchRule.matches(
                                preApproval, authorisation("80.00", "CZK", DEMO_SHOP, FROM)))
                .isFalse();
        assertThat(
                        PreApprovalMatchRule.matches(
                                preApproval,
                                authorisation(
                                        "80.00",
                                        "EUR",
                                        MerchantReference.parse("other-shop"),
                                        FROM)))
                .isFalse();
        assertThat(
                        PreApprovalMatchRule.matches(
                                preApproval,
                                authorisation("80.00", "EUR", DEMO_SHOP, FROM.minusMillis(1))))
                .isFalse();
        assertThat(
                        PreApprovalMatchRule.matches(
                                preApproval,
                                authorisation("80.00", "EUR", DEMO_SHOP, UNTIL.plusMillis(1))))
                .isFalse();
    }

    private static Authorisation authorisation(
            String amount, String currency, MerchantReference merchant, Instant at) {
        return new Authorisation("proc-auth-1", Money.of(amount, currency), merchant, at);
    }
}
