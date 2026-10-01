package com.wrb.devica.board.domain;

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
public class BoardPost extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usage_purpose_id", nullable = false)
    private UsagePurpose usagePurpose;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    private BoardPost(UsagePurpose usagePurpose, String title, String content) {
        this.usagePurpose = usagePurpose;
        this.title = title;
        this.content = content;
    }

    public static BoardPost of(UsagePurpose usagePurpose, String title, String content) {
        return new BoardPost(usagePurpose, title, content);
    }
}
