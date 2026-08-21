package com.avas.platform.project;

public interface PlanningParameterClient {
    /**
     * Proposes the three bounded parameter variants for a project.
     *
     * @param envelope the buildable envelope already resolved for this project, or {@code null}.
     *                 Passed in rather than re-derived so the programme is sized against the same
     *                 geometry the layout is packed into. A room programme scaled from the plot's
     *                 bounding box is scaled for a plot that does not exist: an irregular outline
     *                 encloses less ground than the rectangle around it, and the setback ring takes
     *                 more again.
     */
    PlanningParameterSet optimize(String tenantId, String projectId, String contextVersion,
                                  BasicDetailsRequest details, BuildableEnvelope envelope);

    /** Retains the pre-envelope arity for callers that have no resolved envelope to offer. */
    default PlanningParameterSet optimize(String tenantId, String projectId, String contextVersion,
            BasicDetailsRequest details) {
        return optimize(tenantId, projectId, contextVersion, details, null);
    }
}
