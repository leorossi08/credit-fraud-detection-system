package com.gateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.dto.TransactionDTO;
import com.gateway.dto.TransactionStatusResponse;
import com.gateway.dto.TransactionSubmissionResponse;
import com.gateway.model.TransactionStatus;
import com.gateway.service.TransactionLifecycleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
@SuppressWarnings("null")
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

        @MockitoBean
    private TransactionLifecycleService lifecycleService;

    @Test
    void submitReturnsAcceptedAndTransactionId() throws Exception {
        TransactionDTO transaction = transaction(12.5);
        when(lifecycleService.submit(any(TransactionDTO.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        new TransactionSubmissionResponse("txn-http", "QUEUED")));

        MvcResult result = mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(transaction)))
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.transactionId").value("txn-http"))
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void submitRejectsAmountThatDoesNotMatchFeatureVector() throws Exception {
        TransactionDTO transaction = new TransactionDTO(
                "txn-http", "card-http", 12.5, "2026-09-26T12:00:00Z", Collections.nCopies(30, 0.0));

        MvcResult result = mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(transaction)))
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("amount must match the last feature."));

        verify(lifecycleService, never()).submit(any(TransactionDTO.class));
    }

    @Test
    void recentReturnsTransactionStatuses() throws Exception {
        TransactionStatusResponse transaction = new TransactionStatusResponse(
                "txn-http", "card-http", 12.5, "2026-09-26T12:00:00Z", TransactionStatus.COMPLETED,
                false, 0.04, null, Instant.parse("2026-09-26T12:00:00Z"), Instant.parse("2026-09-26T12:00:01Z"));
        when(lifecycleService.recent(25)).thenReturn(List.of(transaction));

        mockMvc.perform(get("/transactions").param("limit", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].transactionId").value("txn-http"))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$[0].fraudProbability").value(0.04));
    }

    @Test
    void submitRejectsInvalidFeatureCount() throws Exception {
        TransactionDTO transaction = new TransactionDTO(
                "txn-http", "card-http", 12.5, "2026-09-26T12:00:00Z", Collections.nCopies(29, 0.0));

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(transaction)))
                .andExpect(status().isBadRequest());

        verify(lifecycleService, never()).submit(any(TransactionDTO.class));
    }

    private TransactionDTO transaction(double amount) {
        var features = new java.util.ArrayList<>(Collections.nCopies(30, 0.0));
        features.set(29, amount);
        return new TransactionDTO("txn-http", "card-http", amount, "2026-09-26T12:00:00Z", features);
    }
}