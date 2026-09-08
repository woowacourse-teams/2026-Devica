package com.wrb.devica.question;

import com.wrb.devica.purpose.UsagePurposeCode;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    /**
     * open-in-view 옵션이 꺼져있으므로 FETCH JOIN 을 사용해 함께 가져온다.
     */
    @Query("SELECT q FROM Question q JOIN FETCH q.options WHERE q.usagePurpose.code = :purpose")
    List<Question> findAllWithOptionsByPurpose(@Param("purpose") UsagePurposeCode purpose);
}
