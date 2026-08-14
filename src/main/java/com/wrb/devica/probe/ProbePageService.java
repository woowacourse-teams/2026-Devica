package com.wrb.devica.probe;

import org.springframework.stereotype.Service;

@Service
public class ProbePageService {

    public ProbePageResponse getPage() {
        return new ProbePageResponse("question-set-v0.1", false);
    }
}
