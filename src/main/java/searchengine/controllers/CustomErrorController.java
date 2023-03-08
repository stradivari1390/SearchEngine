package searchengine.controllers;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.WebRequest;

import java.util.Objects;

@Controller
public class CustomErrorController implements ErrorController {

    @RequestMapping(value = "/error")
    public String error() {
        return "error";
    }

    @ExceptionHandler(Exception.class)
    public String handleException(Exception e, WebRequest request, Model model) {
        Integer statusCode = (Integer) request.getAttribute("javax.servlet.error.status_code", WebRequest.SCOPE_REQUEST);
        model.addAttribute("status", Objects.requireNonNullElse(statusCode, "500"));
        model.addAttribute("message", e.getMessage());
        return "error";
    }
}