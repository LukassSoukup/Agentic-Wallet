package dev.agenticwallet.spike.simulators.demoshop;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OffersController.class)
class OffersControllerTest {

    @Autowired MockMvc mvc;

    @Test
    void listsSyntheticOffers() throws Exception {
        mvc.perform(get("/offers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].synthetic").value(true))
                .andExpect(jsonPath("$[0].unitPrice").value("29.90"));
    }
}
