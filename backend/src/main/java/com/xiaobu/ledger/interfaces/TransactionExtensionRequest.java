package com.xiaobu.ledger.interfaces;

import com.xiaobu.ledger.application.TransactionExtensionChange;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

record TransactionExtensionRequest(
    @NotBlank @Size(max = 32) String type,
    @NotNull TransactionExtensionChange.Operation operation,
    JsonNode detail) {}
