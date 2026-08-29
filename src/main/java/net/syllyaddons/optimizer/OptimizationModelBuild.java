package net.syllyaddons.optimizer;

import java.util.List;
import java.util.Optional;

public record OptimizationModelBuild(Optional<OptimizationModel> model, List<String> diagnostics) {
    public OptimizationModelBuild {
        model = model == null ? Optional.empty() : model;
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public static OptimizationModelBuild unavailable(String diagnostic) {
        return new OptimizationModelBuild(Optional.empty(), List.of(diagnostic));
    }
}
