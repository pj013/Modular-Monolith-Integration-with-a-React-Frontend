package edu.cit.aaron.channel;

final class MarketplaceOrderContext {

    private static final ThreadLocal<Boolean> DECISION_PENDING =
            ThreadLocal.withInitial(() -> false);

    private MarketplaceOrderContext() {
    }

    static void begin() {
        DECISION_PENDING.set(true);
    }

    static boolean decisionPending() {
        return DECISION_PENDING.get();
    }

    static void end() {
        DECISION_PENDING.remove();
    }
}
