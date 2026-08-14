package com.wrb.devica.probe;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ProbePageController {

    private final ProbePageService probePageService;

    public ProbePageController(ProbePageService probePageService) {
        this.probePageService = probePageService;
    }

    @GetMapping("/")
    public String show(Model model) {
        model.addAttribute("page", probePageService.getPage());
        return "pages/probe/index";
    }
}
