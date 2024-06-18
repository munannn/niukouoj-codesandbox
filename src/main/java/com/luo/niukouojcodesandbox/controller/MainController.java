package com.luo.niukouojcodesandbox.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author 木南
 * @version 1.0
 */
@RestController
public class MainController {

    @GetMapping("/ok")
    public String getOk() {
        return "ok";
    }
}
