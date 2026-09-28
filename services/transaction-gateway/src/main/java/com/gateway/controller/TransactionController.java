package com.gateway.controller;

import com.gateway.dto.TransactionDTO;
import com.gateway.dto.TransactionStatusResponse;
import com.gateway.service.TransactionLifecycleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.List;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionLifecycleService lifecycleService;

    public TransactionController(TransactionLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @PostMapping
    public CompletableFuture<ResponseEntity<?>> submit(
            @Valid @RequestBody TransactionDTO transaction) {
        if (Double.compare(transaction.amount(), transaction.features().get(29)) != 0) {
            return CompletableFuture.completedFuture(ResponseEntity.badRequest()
                    .body(Map.of("error", "amount must match the last feature.")));
        }

        return lifecycleService.submit(transaction)
                .thenApply(body -> ResponseEntity.accepted().body(body));
    }

    @GetMapping
    public List<TransactionStatusResponse> recent(
            @RequestParam(defaultValue = "50") int limit) {
        return lifecycleService.recent(limit);
    }

    @GetMapping("/{transactionId}")
    public TransactionStatusResponse find(@PathVariable String transactionId) {
        return lifecycleService.find(transactionId);
    }
}