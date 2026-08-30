package com.avas.platform.project;

/**
 * Asks AVAS AI what home this household should be planned.
 *
 * <p>Asked before the customer approves their brief rather than after. The programme <em>is</em> the
 * brief: a recommendation promising two bedrooms and a drawing packed for four is one question
 * answered twice, and until this existed the platform answered it both ways — once here from the
 * headcount alone, and once again in the parameter variants, which then had to copy the first answer
 * to avoid contradicting it.</p>
 */
public interface ProgrammeClient {
    /**
     * The programme for this brief, never {@code null} and never a failure.
     *
     * @param envelope the buildable envelope already resolved for this project, or {@code null}.
     *                 It is what makes this a decision rather than a guess: bedrooms are capped by
     *                 the area this plot can actually enclose, and a programme sized from the plot's
     *                 bounding box is sized for ground that does not exist.
     */
    HouseholdProgramme plan(String tenantId, String projectId, String contextVersion,
                            BasicDetailsRequest details, BuildableEnvelope envelope);
}
