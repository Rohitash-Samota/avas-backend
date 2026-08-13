package com.avas.platform.project;

public interface PlanningParameterClient {
    PlanningParameterSet optimize(String tenantId, String projectId, String contextVersion,
                                  BasicDetailsRequest details);
}
