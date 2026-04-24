package com.movie.booking.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Service
public class DiscountService {

    private static final BigDecimal THIRD_TICKET_DISCOUNT = new BigDecimal("0.50");
    private static final BigDecimal AFTERNOON_SHOW_DISCOUNT = new BigDecimal("0.20");

    // Afternoon show time range: 12:00 PM to 5:00 PM
    private static final LocalTime AFTERNOON_START = LocalTime.of(12, 0);
    private static final LocalTime AFTERNOON_END = LocalTime.of(17, 0);

    /**
     * Calculate total price with applicable discounts
     */
    public DiscountCalculation calculateDiscount(List<String> seatNumbers, LocalTime showTime, BigDecimal basePrice) {
        BigDecimal totalPrice = basePrice.multiply(BigDecimal.valueOf(seatNumbers.size()));
        BigDecimal discountAmount = BigDecimal.ZERO;
        String discountDescription = "No discount applied";

        // 50% discount on third ticket
        if (seatNumbers.size() >= 3) {
            BigDecimal thirdTicketDiscount = basePrice.multiply(THIRD_TICKET_DISCOUNT);
            discountAmount = discountAmount.add(thirdTicketDiscount);
            discountDescription = "50% discount on third ticket";
        }

        // 20% discount for afternoon shows
        if (isAfternoonShow(showTime)) {
            BigDecimal afternoonDiscount = totalPrice.multiply(AFTERNOON_SHOW_DISCOUNT);
            discountAmount = discountAmount.add(afternoonDiscount);
            discountDescription = discountDescription.equals("No discount applied") ?
                    "20% discount for afternoon show" :
                    discountDescription + " + 20% discount for afternoon show";
        }

        BigDecimal finalPrice = totalPrice.subtract(discountAmount);

        return new DiscountCalculation(
                totalPrice,
                discountAmount,
                finalPrice,
                discountDescription
        );
    }

    /**
     * Check if show time is in afternoon (12 PM - 5 PM)
     */
    private boolean isAfternoonShow(LocalTime showTime) {
        return !showTime.isBefore(AFTERNOON_START) && showTime.isBefore(AFTERNOON_END);
    }

    /**
     * Discount calculation result
     */
    public static class DiscountCalculation {
        private final BigDecimal originalPrice;
        private final BigDecimal discountAmount;
        private final BigDecimal finalPrice;
        private final String discountDescription;

        public DiscountCalculation(BigDecimal originalPrice, BigDecimal discountAmount,
                                 BigDecimal finalPrice, String discountDescription) {
            this.originalPrice = originalPrice;
            this.discountAmount = discountAmount;
            this.finalPrice = finalPrice;
            this.discountDescription = discountDescription;
        }

        public BigDecimal getOriginalPrice() { return originalPrice; }
        public BigDecimal getDiscountAmount() { return discountAmount; }
        public BigDecimal getFinalPrice() { return finalPrice; }
        public String getDiscountDescription() { return discountDescription; }
    }
}
