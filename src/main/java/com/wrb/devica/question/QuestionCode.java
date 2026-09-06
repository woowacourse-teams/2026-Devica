package com.wrb.devica.question;

import com.wrb.devica.question.option.AiCodingTool;
import com.wrb.devica.question.option.BuildWait;
import com.wrb.devica.question.option.CurrentMacCpu;
import com.wrb.devica.question.option.CurrentMemory;
import com.wrb.devica.question.option.CurrentOs;
import com.wrb.devica.question.option.CurrentStorage;
import com.wrb.devica.question.option.CurrentWindowsCpu;
import com.wrb.devica.question.option.DevEnvironmentSetup;
import com.wrb.devica.question.option.Ide;
import com.wrb.devica.question.option.Overheating;
import com.wrb.devica.question.option.PreferredOs;
import com.wrb.devica.question.option.ProgrammingLanguage;
import com.wrb.devica.question.option.Slowdown;
import com.wrb.devica.question.option.StorageShortage;
import com.wrb.devica.question.option.UsagePeriod;
import java.util.List;
import lombok.Getter;

/**
 * 질문의 구조를 갖는다 — 어떤 선택지를 쓰는지, 몇 개를 고를 수 있는지, 배타 선택지가 무엇인지,
 * 어떤 답변에 딸린 질문인지. 제목·문구와 목적별 구성(어떤 질문을 쓰는지)은 DB 가 갖는다.
 * <p>
 * 선언 순서가 곧 화면에 묻는 순서다. 의존 대상을 생성자 인자로 직접 가리키므로 뒤에 선언된 질문은
 * 참조할 수 없고(illegal forward reference), 트리거가 되는 질문이 앞에 온다.
 * <p>
 * 아래 주석은 시드 문구의 요지다. 화면에 나가는 문구의 원천은 DB 이므로 토씨까지 맞추지 않는다.
 */
@Getter
public enum QuestionCode {

    // 현재 사양 다섯 질문 — 모름, 미입력은 답을 보내지 않는 것으로 표현한다. UNKNOWN 선택지를 두지 않는다.

    /**
     * 현재 사양 — 쓰고 있는 노트북의 OS
     */
    CURRENT_OS(CurrentOs.values()),

    /**
     * 현재 사양 — Mac 의 프로세서
     */
    CURRENT_MAC_CPU(CurrentMacCpu.values(), new QuestionDependency(CURRENT_OS, CurrentOs.MACOS)),

    /**
     * 현재 사양 — Windows 노트북의 프로세서
     */
    CURRENT_WINDOWS_CPU(CurrentWindowsCpu.values(), new QuestionDependency(CURRENT_OS, CurrentOs.WINDOWS)),

    /**
     * 현재 사양 — 메모리
     */
    CURRENT_MEMORY(CurrentMemory.values()),

    /**
     * 현재 사양 — 저장 공간
     */
    CURRENT_STORAGE(CurrentStorage.values()),

    /**
     * 어떤 노트북을 생각하고 계신가요?
     */
    PREFERRED_OS(PreferredOs.values()),

    /**
     * 주로 다루는 언어는 무엇인가요? (여러 개 선택)
     */
    PROGRAMMING_LANGUAGE(ProgrammingLanguage.values(), QuestionInputType.MULTI, ProgrammingLanguage.UNDECIDED),

    /**
     * 어떤 IDE 를 쓰나요?
     */
    IDE(Ide.values()),

    /**
     * AI 코딩 도구를 쓰나요?
     */
    AI_CODING_TOOL(AiCodingTool.values()),

    /**
     * 개발 환경을 어떻게 띄우나요?
     */
    DEV_ENVIRONMENT_SETUP(DevEnvironmentSetup.values()),

    /**
     * 프로그램을 여러 개 켜두면 멈추거나 껐다 켜야 했나요?
     */
    SLOWDOWN(Slowdown.values()),

    /**
     * 용량이 부족해서 뭔가를 지우거나 옮긴 적이 있나요?
     */
    STORAGE_SHORTAGE(StorageShortage.values()),

    /**
     * 빌드나 테스트가 끝나기를 기다리는 게 답답했나요?
     */
    BUILD_WAIT(BuildWait.values(), new QuestionDependency(PROGRAMMING_LANGUAGE, ProgrammingLanguage.JAVA_FAMILY)),

    /**
     * 개발할 때 노트북이 뜨거워지거나 팬이 크게 도나요?
     */
    OVERHEATING(Overheating.values()),

    /**
     * 이 노트북을 얼마나 쓸 생각인가요?
     */
    USAGE_PERIOD(UsagePeriod.values());

    private final List<OptionCode> options;
    private final QuestionInputType inputType;
    private final OptionCode exclusiveOption;
    private final QuestionDependency dependency;

    QuestionCode(OptionCode[] options) {
        this(options, QuestionInputType.SINGLE, null, null);
    }

    QuestionCode(OptionCode[] options, QuestionDependency dependency) {
        this(options, QuestionInputType.SINGLE, null, dependency);
    }

    QuestionCode(OptionCode[] options, QuestionInputType inputType, OptionCode exclusiveOption) {
        this(options, inputType, exclusiveOption, null);
    }

    QuestionCode(OptionCode[] options, QuestionInputType inputType, OptionCode exclusiveOption,
                 QuestionDependency dependency) {
        this.options = List.of(options);
        this.inputType = inputType;
        this.exclusiveOption = exclusiveOption;
        this.dependency = dependency;
    }
}
