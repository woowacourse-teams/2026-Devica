package com.wrb.devica.faq;

import com.wrb.devica.common.BaseTimeEntity;
import com.wrb.devica.purpose.UsagePurpose;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "faq")
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
    private String question;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    @Column(nullable = false)
    private boolean published;

    @Column(nullable = false)
    private int displayOrder;

    private Faq(UsagePurpose usagePurpose, String slug, String question, String answer,
                boolean published, int displayOrder) {
        this.usagePurpose = usagePurpose;
        this.slug = slug;
        this.question = question;
        this.answer = answer;
        this.published = published;
        this.displayOrder = displayOrder;
    }

    public static Faq home(String slug, String question, String answer, boolean published,
                           int displayOrder) {
        return new Faq(null, slug, question, answer, published, displayOrder);
    }

    public static Faq forUsagePurpose(UsagePurpose usagePurpose, String slug, String question,
                                      String answer, boolean published, int displayOrder) {
        return new Faq(usagePurpose, slug, question, answer, published, displayOrder);
    }
}
