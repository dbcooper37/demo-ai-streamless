package com.demo.websocket.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {
    
    private boolean valid;
    private String errorMessage;
    private List<String> errors;
    
    public static ValidationResult success() {
        return ValidationResult.builder()
            .valid(true)
            .errors(new ArrayList<>())
            .build();
    }
    
    public static ValidationResult failure(String errorMessage) {
        return ValidationResult.builder()
            .valid(false)
            .errorMessage(errorMessage)
            .errors(List.of(errorMessage))
            .build();
    }
    
    public static ValidationResult failure(List<String> errors) {
        return ValidationResult.builder()
            .valid(false)
            .errorMessage(String.join("; ", errors))
            .errors(errors)
            .build();
    }
}
