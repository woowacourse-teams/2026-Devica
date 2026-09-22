package com.wrb.devica.faq.domain;

import com.wrb.devica.common.BaseTimeEntity;
import com.wrb.devica.purpose.domain.UsagePurpose;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Faq extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usage_purpose_id")
    private UsagePurpose usagePurpose;

    @Column(nullable = false, unique = true, length = 255)
    private String slug;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private boolean published;

    @Column(nullable = false)
    private int displayOrder;

    private Faq(UsagePurpose usagePurpose, String slug, String title, String content,
                boolean published, int displayOrder) {
        this.usagePurpose = usagePurpose;
        this.slug = slug;
        this.title = title;
        this.content = content;
        this.published = published;
        this.displayOrder = displayOrder;
    }

    public static Faq service(String slug, String question, String answer, boolean published,
                              int displayOrder) {
        return new Faq(null, slug, question, answer, published, displayOrder);
    }

    public static Faq forUsagePurpose(UsagePurpose usagePurpose, String slug, String question,
                                      String answer, boolean published, int displayOrder) {
        return new Faq(usagePurpose, slug, question, answer, published, displayOrder);
    }
}
