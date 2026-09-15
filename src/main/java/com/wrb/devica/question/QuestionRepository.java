package com.wrb.devica.question;

import com.wrb.devica.purpose.UsagePurposeCode;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    /**
     * open-in-view 옵션이 꺼져있으므로 Entity Graph 로 함께 가져온다.
     */
    @EntityGraph(attributePaths = "options")
    List<Question> findAllByUsagePurpose_Code(UsagePurposeCode code);
}
