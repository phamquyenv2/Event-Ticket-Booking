package com.quyen.geekticket.domain.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateSuspiciousRequest {

    @NotNull(message = "Suspicious flag is required")
    private Boolean suspicious;

    @NotBlank(message = "Reason is required when setting suspicious flag")
    private String reason;
}
