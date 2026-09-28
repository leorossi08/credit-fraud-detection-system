package com.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TransactionDTO(
    @NotBlank
    String transactionId,
    @NotBlank
    String cardId,
    @PositiveOrZero
    double amount,
    @NotBlank
    String timestamp,
    @NotNull
    @Size(min = 30, max = 30)
    List<@NotNull Double> features
) {}