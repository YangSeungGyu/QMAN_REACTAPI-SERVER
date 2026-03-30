package com.reactapi.api.react;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;


//리엑트 포워딩용
@Controller
public class ReactController {
    @RequestMapping(value = "/{path:[^\\.]*}")
    public String redirect() {
        return "forward:/index.html";
    }
}