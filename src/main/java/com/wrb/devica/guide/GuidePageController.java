package com.wrb.devica.guide;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class GuidePageController {

    @GetMapping("/guide")
    public String show() {
        return "pages/guide/index";
    }

    @GetMapping("/guide/why-24gb-512gb")
    public String showWhy24Gb512Gb() {
        return "pages/guide/why-24gb-512gb";
    }

    @GetMapping("/guide/what-to-consider")
    public String showWhatToConsider() {
        return "pages/guide/what-to-consider";
    }

    @GetMapping("/guide/adjust-for-your-needs")
    public String showAdjustForYourNeeds() {
        return "pages/guide/adjust-for-your-needs";
    }
}
