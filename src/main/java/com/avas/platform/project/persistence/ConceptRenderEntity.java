package com.avas.platform.project.persistence;

import com.avas.platform.common.persistence.AbstractLongIdEntity;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * One generated picture, kept so a concept is never drawn twice.
 *
 * <p>Stored rather than regenerated because an image model is not a pure function of its prompt: ask
 * it for the same house twice and it returns two different houses, with different roofs, different
 * windows and a different number of storeys in the picture. A customer who reopens their own concept
 * and is shown a new one has no way to tell which of the two is theirs, and neither has anyone they
 * showed it to. Persisting the first answer is what makes the illustration a property of the concept
 * instead of a property of the moment it was looked at.</p>
 *
 * <p>It is also the only artefact here that costs money per view. Everything else on that dialog —
 * the layout sheet, the floor plans, the massing, the PDF — is drawn from geometry the platform
 * already holds and can be redrawn for nothing.</p>
 *
 * <p>Keyed by drawing, viewpoint and brief together: those three are what the picture is <em>of</em>,
 * and a change to any of them is a different picture rather than a stale one. The drawing id carries
 * the version, so a regenerated layout asks for its own render rather than inheriting the previous
 * one.</p>
 */
@Entity
@Table(name = "concept_renders",
        uniqueConstraints = @UniqueConstraint(name = "uq_concept_render_key",
                columnNames = {"drawing_id", "style", "brief_key"}))
class ConceptRenderEntity extends AbstractLongIdEntity {
    @Column(name = "drawing_id", nullable = false, length = 120) private String drawingId;
    @Column(nullable = false, length = 40) private String style;
    /** Normalised so that "no brief" is one value rather than null, which no unique index can compare. */
    @Column(name = "brief_key", nullable = false, length = 400) private String briefKey;
    @Column(nullable = false, length = 60) private String mediaType;
    @Lob @Column(nullable = false, columnDefinition = "LONGBLOB") private byte[] image;
    @Lob @Column(columnDefinition = "TEXT") private String prompt;
    @Column(length = 80) private String provider;
    @Column(length = 120) private String model;
    @Lob @Column(columnDefinition = "TEXT") private String warningsJson;
    @Column(nullable = false) private Instant createdAt;

    protected ConceptRenderEntity() {}

    ConceptRenderEntity(String drawingId, String style, String briefKey, String mediaType, byte[] image,
                        String prompt, String provider, String model, String warningsJson) {
        this.drawingId = drawingId;
        this.style = style;
        this.briefKey = briefKey;
        this.createdAt = Instant.now();
        update(mediaType, image, prompt, provider, model, warningsJson);
    }

    void update(String mediaType, byte[] image, String prompt, String provider, String model, String warningsJson) {
        this.createdAt = Instant.now();
        this.mediaType = mediaType;
        this.image = image == null ? new byte[0] : image.clone();
        this.prompt = prompt;
        this.provider = provider;
        this.model = model;
        this.warningsJson = warningsJson;
    }

    String mediaType() { return mediaType; }
    byte[] image() { return image == null ? new byte[0] : image.clone(); }
    String prompt() { return prompt; }
    String provider() { return provider; }
    String model() { return model; }
    String warningsJson() { return warningsJson; }
}
