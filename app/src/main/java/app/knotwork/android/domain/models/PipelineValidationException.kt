package app.knotwork.android.domain.models

/**
 * Exception thrown when pipeline validation fails.
 *
 * @property errors The list of validation errors.
 */
class PipelineValidationException(val errors: List<PipelineValidationError>) :
    Exception("Pipeline validation failed with ${errors.size} errors.")
