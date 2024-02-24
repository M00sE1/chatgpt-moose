package com.moose.gptmoose.controller;

import com.moose.gptmoose.service.GptChatService;

import com.moose.gptmoose.util.RequestUtil;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author Moose
 */
@RestController
public class ChatController {
    @Resource
    private GptChatService gptChatService;

    /**
     * 流式输出
     * @param content
     * @param request
     * @param response
     * @throws IOException
     */
    @PostMapping("/streamChat")
    public void streamChat(@RequestBody String content, HttpServletRequest request,HttpServletResponse response) throws IOException {
        String id = request.getSession().getId();
        gptChatService.streamChat(id,content,response);

    }

    @PostMapping("createImage")
    public void createImage(String content){
        gptChatService.createImage(content);
    }
}
