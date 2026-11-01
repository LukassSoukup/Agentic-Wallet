package dev.agenticwallet.spike.simulators.demoshop;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Synthetic offers; every value here is labelled synthetic. */
@RestController
class OffersController {

    record Offer(
            String merchantItemId,
            String title,
            String unitPrice,
            String currency,
            boolean synthetic) {}

    @GetMapping("/offers")
    List<Offer> offers() {
        return List.of(
                new Offer("SYN-0001", "Synthetic kettle", "29.90", "EUR", true),
                new Offer("SYN-0002", "Synthetic flight PRG-LHR", "89.00", "EUR", true));
    }
}
