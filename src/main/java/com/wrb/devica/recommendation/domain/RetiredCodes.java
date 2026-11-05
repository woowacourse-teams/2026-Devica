package com.wrb.devica.recommendation.domain;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.Tag;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

public record RetiredCodes(Integer keepMonths, List<RetiredCode> codes) {

    public static final RetiredCodes NONE = new RetiredCodes(null, List.of());

    private static final JsonMapper MAPPER = JsonMapper.builder()
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();

    public RetiredCodes {
        if (codes == null) {
            codes = List.of();
        }

        codes = List.copyOf(codes);
    }

    public static RetiredCodes read(InputStream input) {
        Object yaml = new Yaml(new DatesAsText()).load(input);

        if (yaml == null) {
            return NONE;
        }

        return MAPPER.convertValue(yaml, RetiredCodes.class);
    }

    public Stream<RetiredCode> activeOn(LocalDate today) {
        return codes.stream()
            .filter(line -> !line.retiredAt().plusMonths(keepMonths).isBefore(today));
    }

    private static class DatesAsText extends SafeConstructor {

        DatesAsText() {
            super(new LoaderOptions());
            yamlConstructors.put(Tag.TIMESTAMP, new ConstructYamlStr());
        }
    }
}
