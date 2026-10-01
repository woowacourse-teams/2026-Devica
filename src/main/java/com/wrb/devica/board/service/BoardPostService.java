package com.wrb.devica.board.service;

import com.wrb.devica.board.domain.BoardPost;
import com.wrb.devica.board.dto.BoardPostCreateRequest;
import com.wrb.devica.board.dto.BoardPostDetailResponse;
import com.wrb.devica.board.dto.BoardPostPageCondition;
import com.wrb.devica.board.dto.BoardPostSummaryResponse;
import com.wrb.devica.board.repository.BoardPostRepository;
import com.wrb.devica.category.domain.ProductCategoryCode;
import com.wrb.devica.common.exception.BusinessErrorCode;
import com.wrb.devica.common.exception.BusinessException;
import com.wrb.devica.purpose.domain.UsagePurpose;
import com.wrb.devica.purpose.domain.UsagePurposeCode;
import com.wrb.devica.purpose.repository.UsagePurposeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardPostService {

    private final BoardPostRepository boardPostRepository;
    private final UsagePurposeRepository usagePurposeRepository;

    @Transactional
    public Long create(String categoryCode, String purposeCode, BoardPostCreateRequest request) {
        UsagePurpose usagePurpose = findUsagePurpose(categoryCode, purposeCode);
        BoardPost post = boardPostRepository.save(BoardPost.of(usagePurpose, request.title(), request.content()));
        return post.getId();
    }

    public Slice<BoardPostSummaryResponse> findPosts(String categoryCode, String purposeCode,
                                                     BoardPostPageCondition pageCondition) {
        UsagePurpose usagePurpose = findUsagePurpose(categoryCode, purposeCode);
        return boardPostRepository.findByUsagePurpose_IdOrderByCreatedAtDescIdDesc(
            usagePurpose.getId(), PageRequest.of(pageCondition.page(), pageCondition.size())
        );
    }

    public BoardPostDetailResponse findPost(Long id) {
        BoardPost post = boardPostRepository.findWithUsagePurposeById(id)
            .orElseThrow(() -> new BusinessException(BusinessErrorCode.BOARD_POST_NOT_FOUND));
        return BoardPostDetailResponse.from(post);
    }

    private UsagePurpose findUsagePurpose(String categoryCode, String purposeCode) {
        ProductCategoryCode category = ProductCategoryCode.from(categoryCode);
        UsagePurposeCode purpose = UsagePurposeCode.from(purposeCode);
        return usagePurposeRepository.findByProductCategory_CodeAndCode(category, purpose)
            .orElseThrow(() -> new BusinessException(BusinessErrorCode.USAGE_PURPOSE_NOT_FOUND));
    }
}
