package com.avas.platform.pricing;

import org.springframework.data.mongodb.repository.MongoRepository;

interface PricingIntelligenceRepository extends MongoRepository<PricingIntelligenceDocument, String> {}
